package ke.ac.kca.cafeteria.table;

import ke.ac.kca.cafeteria.reporting.AuditService;
import jakarta.servlet.http.HttpSession;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Table management and token resolution (FR-009 to FR-011). Every change and its audit record commit together.
 * Audit entries and logs name the table but never contain a token.
 */
@Service
public class TableService {

    private static final Logger log = LoggerFactory.getLogger(TableService.class);

    private static final String TARGET = "TABLE";
    private static final String SUCCESS = "SUCCESS";
    private static final Pattern LABEL = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9 ._#/-]{0,39}$");
    private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)$");

    static final String CREATED = "TABLE_CREATED";
    static final String RENAMED = "TABLE_RENAMED";
    static final String DEACTIVATED = "TABLE_DEACTIVATED";
    static final String REACTIVATED = "TABLE_REACTIVATED";
    static final String ROTATED = "TABLE_QR_ROTATED";

    private final CafeteriaTableRepository tables;
    private final AuditService audit;
    private final DateTimeFormatter timeFormat;

    public TableService(CafeteriaTableRepository tables, AuditService audit,
                        @Value("${app.display-timezone:Africa/Nairobi}") String zoneId) {
        this.tables = tables;
        this.audit = audit;
        this.timeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.of(zoneId));
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public List<TableView> list() {
        return tables.findAll().stream()
                .sorted(Comparator.comparing(CafeteriaTable::getLabel, TableService::compareLabels))
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public TableView get(String publicId) {
        return view(find(publicId));
    }

    // ---- administration ----

    @Transactional
    public IssuedQr create(String actor, String rawLabel) {
        String label = clean(rawLabel);
        List<String> problems = new ArrayList<>(validateLabel(label));
        if (problems.isEmpty() && tables.existsByLabelIgnoreCase(label)) {
            problems.add("A table with that label already exists.");
        }
        failIfAny(problems);

        String token = TableToken.generate();
        CafeteriaTable table = new CafeteriaTable(label, TableToken.hash(token));
        tables.save(table);
        audit.record(actor, CREATED, TARGET, table.getPublicId(), SUCCESS, "label=" + label);
        return new IssuedQr(table.getPublicId(), label, token);
    }

    @Transactional
    public void rename(String actor, String publicId, String rawLabel) {
        CafeteriaTable table = find(publicId);
        String label = clean(rawLabel);
        List<String> problems = new ArrayList<>(validateLabel(label));
        if (problems.isEmpty() && tables.existsByLabelIgnoreCaseAndIdNot(label, table.getId())) {
            problems.add("A table with that label already exists.");
        }
        failIfAny(problems);

        String before = table.getLabel();
        if (!before.equals(label)) {
            table.rename(label);
            audit.record(actor, RENAMED, TARGET, table.getPublicId(), SUCCESS, "label: " + before + " -> " + label);
        }
    }

    /** Switches a table off. Its token is replaced, so the printed QR stays dead even if the table returns. */
    @Transactional
    public void deactivate(String actor, String publicId) {
        CafeteriaTable table = find(publicId);
        if (!table.isActive()) {
            return;
        }
        table.deactivate(TableToken.hash(TableToken.generate()));
        audit.record(actor, DEACTIVATED, TARGET, table.getPublicId(), SUCCESS, "label=" + table.getLabel());
    }

    /** Switches a table back on and issues a new token. The administrator must print the new QR. */
    @Transactional
    public IssuedQr reactivate(String actor, String publicId) {
        CafeteriaTable table = find(publicId);
        if (table.isActive()) {
            throw new TableException("That table is already active.");
        }
        String token = TableToken.generate();
        table.reactivate(TableToken.hash(token));
        audit.record(actor, REACTIVATED, TARGET, table.getPublicId(), SUCCESS, "label=" + table.getLabel());
        return new IssuedQr(table.getPublicId(), table.getLabel(), token);
    }

    /** Issues a new token for an active table, for example when a label is lost or copied. */
    @Transactional
    public IssuedQr rotate(String actor, String publicId) {
        CafeteriaTable table = find(publicId);
        if (!table.isActive()) {
            throw new TableException("Reactivate the table to issue a new QR code.");
        }
        String token = TableToken.generate();
        table.rotate(TableToken.hash(token));
        audit.record(actor, ROTATED, TARGET, table.getPublicId(), SUCCESS, "label=" + table.getLabel());
        return new IssuedQr(table.getPublicId(), table.getLabel(), token);
    }

    // ---- students ----

    /** Looks up what a student scanned or typed. A token only identifies a table; it never signs anyone in. */
    @Transactional(readOnly = true)
    public TableResolution resolve(String input) {
        Optional<String> token = TableToken.extract(input);
        if (token.isEmpty()) {
            log.info("Table code rejected: {}", TableResolution.Status.MALFORMED);
            return TableResolution.failed(TableResolution.Status.MALFORMED);
        }
        Optional<CafeteriaTable> found = tables.findByTokenHash(TableToken.hash(token.get()));
        if (found.isEmpty()) {
            log.info("Table code rejected: {}", TableResolution.Status.UNKNOWN);
            return TableResolution.failed(TableResolution.Status.UNKNOWN);
        }
        CafeteriaTable table = found.get();
        if (!table.isActive()) {
            log.info("Table code rejected: {}", TableResolution.Status.INACTIVE);
            return TableResolution.failed(TableResolution.Status.INACTIVE);
        }
        return TableResolution.valid(new SelectedTable(table.getPublicId(), table.getLabel()));
    }

    /** The student's table, checked again against the database so a switched-off table stops working at once. */
    @Transactional(readOnly = true)
    public Optional<SelectedTable> currentTable(HttpSession session) {
        Optional<SelectedTable> chosen = TableSession.read(session);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        Optional<CafeteriaTable> table = tables.findByPublicId(chosen.get().getPublicId())
                .filter(CafeteriaTable::isActive);
        if (table.isEmpty()) {
            TableSession.clear(session);
            return Optional.empty();
        }
        return Optional.of(new SelectedTable(table.get().getPublicId(), table.get().getLabel()));
    }

    /** A table by its public ID, only when it is switched on. Used when an order is placed. */
    @Transactional(readOnly = true)
    public Optional<TableRef> activeTable(String publicId) {
        return tables.findByPublicId(publicId)
                .filter(CafeteriaTable::isActive)
                .map(table -> new TableRef(table.getId(), table.getPublicId(), table.getLabel()));
    }

    // ---- helpers ----

    private TableView view(CafeteriaTable table) {
        return new TableView(table.getPublicId(), table.getLabel(), table.isActive(),
                timeFormat.format(table.getTokenIssuedAt()));
    }

    private CafeteriaTable find(String publicId) {
        return tables.findByPublicId(publicId).orElseThrow(() -> new TableException("That table was not found."));
    }

    private static List<String> validateLabel(String label) {
        List<String> problems = new ArrayList<>();
        if (!LABEL.matcher(label).matches()) {
            problems.add("The label must be 1 to 40 characters: letters, digits, spaces and . _ # / - only, "
                    + "starting with a letter or digit. For example: Table 7.");
        }
        return problems;
    }

    private static void failIfAny(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new TableException(problems);
        }
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    /** Orders "Table 2" before "Table 10". */
    static int compareLabels(String a, String b) {
        Matcher ma = TRAILING_NUMBER.matcher(a);
        Matcher mb = TRAILING_NUMBER.matcher(b);
        if (ma.matches() && mb.matches()) {
            int byPrefix = ma.group(1).compareToIgnoreCase(mb.group(1));
            if (byPrefix != 0) {
                return byPrefix;
            }
            return new BigInteger(ma.group(2)).compareTo(new BigInteger(mb.group(2)));
        }
        return a.compareToIgnoreCase(b);
    }
}
