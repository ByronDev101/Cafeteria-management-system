package ke.ac.kca.cafeteria.credit;

import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.identity.UserRepository;
import ke.ac.kca.cafeteria.menu.PriceParser;
import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.OrderException;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.reporting.AuditService;
import ke.ac.kca.cafeteria.reporting.ManagerGuard;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlled student credit, kept in one place (FR-027 to FR-032).
 *
 * <p>A manager gives each student a limit. When a student orders with credit, the order's total is put on hold
 * (reserved) if it fits under the limit. When staff accept the order the hold becomes a charge (posted). If the order
 * is cancelled first the hold is released; if it is cancelled after acceptance the charge is reversed. When a
 * student pays what they owe, a manager records it. Everything is a new line in an append-only ledger and an audit
 * entry; nothing is edited. Amounts are prototype records: no real money moves through this system.
 *
 * <p>The account row is locked while a hold is placed, so two orders placed at the same moment cannot together go
 * over the limit.
 */
@Service
public class CreditService {

    public static final String LIMIT_SET = "CREDIT_LIMIT_SET";
    public static final String RESERVED = "CREDIT_RESERVED";
    public static final String RELEASED = "CREDIT_RELEASED";
    public static final String POSTED = "CREDIT_POSTED";
    public static final String REVERSED = "CREDIT_REVERSED";
    public static final String SETTLED = "CREDIT_SETTLED";

    public static final BigDecimal MAX_LIMIT = new BigDecimal("50000.00");
    public static final int OVERVIEW_ACCOUNTS = 100;
    public static final int OVERVIEW_SEARCH = 50;
    public static final int RECENT_ENTRIES = 20;

    private static final String TARGET = "CREDIT";
    private static final String SUCCESS = "SUCCESS";
    private static final String PROTOTYPE = "prototype record, no real money";
    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,7}(\\.\\d{1,2})?$");
    private static final int QUERY_MAX = 40;
    private static final String STUDENT_NOT_FOUND = "That student was not found.";

    private final CreditAccountRepository accounts;
    private final CreditEntryRepository entries;
    private final UserRepository users;
    private final AuditService audit;
    private final DateTimeFormatter timeFormat;

    public CreditService(CreditAccountRepository accounts, CreditEntryRepository entries, UserRepository users,
                         AuditService audit, @Value("${app.display-timezone:Africa/Nairobi}") String zoneId) {
        this.accounts = accounts;
        this.entries = entries;
        this.users = users;
        this.audit = audit;
        this.timeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.of(zoneId));
    }

    // ---- following an order ----

    /**
     * Puts an order's total on hold. Called when an order paid with credit is placed. Refuses, and so cancels the
     * whole order, when the student has no credit or the total is more than they have available.
     */
    @Transactional
    public void reserve(CustomerOrder order) {
        if (order.getPaymentMethod() != PaymentMethod.CREDIT) {
            return;
        }
        AppUser student = order.getStudent();
        CreditAccount account = accounts.findForUpdate(student.getId())
                .orElseThrow(() -> new OrderException(
                        "You do not have cafeteria credit. Choose another way to pay, or ask the cafeteria manager."));
        if (!entries.totalsForOrder(order.getId()).isEmpty()) {
            return;     // already held: placing the same order twice must never hold twice
        }
        CreditBalance balance = balanceFrom(true, account.getCreditLimit(), entries.totalsForStudent(student.getId()));
        BigDecimal total = order.getTotal();
        if (total.compareTo(balance.getAvailable()) > 0) {
            throw new OrderException("This order costs " + PriceParser.format(total) + " but you have "
                    + balance.getAvailableText() + " of cafeteria credit available. "
                    + "Choose another way to pay, or remove some items.");
        }
        entries.save(new CreditEntry(student, order, CreditEntryType.RESERVE, total, student.getUsername(), null));
        audit.record(student.getUsername(), RESERVED, TARGET, order.getPublicId(), SUCCESS,
                "order=" + order.getOrderNumber() + "; amount=" + total.toPlainString()
                        + "; available after=" + balance.getAvailable().subtract(total).toPlainString()
                        + "; " + PROTOTYPE);
    }

    /** Turns the hold for an order into a charge. Called when staff accept an order. Does nothing for other orders. */
    @Transactional
    public void postOnAcceptance(String actor, CustomerOrder order) {
        if (order.getPaymentMethod() != PaymentMethod.CREDIT) {
            return;
        }
        Map<CreditEntryType, BigDecimal> sums = sumsByType(entries.totalsForOrder(order.getId()));
        BigDecimal held = held(sums);
        if (held.signum() <= 0) {
            if (sums.getOrDefault(CreditEntryType.POST, BigDecimal.ZERO).signum() > 0) {
                return;     // already charged
            }
            throw new OrderException("No credit is held for order " + order.getOrderNumber()
                    + ", so it cannot be accepted. Ask the student to place it again.");
        }
        entries.save(new CreditEntry(order.getStudent(), order, CreditEntryType.POST, held, actor, null));
        audit.record(actor, POSTED, TARGET, order.getPublicId(), SUCCESS,
                "order=" + order.getOrderNumber() + "; amount=" + held.toPlainString() + "; hold -> charge; "
                        + PROTOTYPE);
    }

    /**
     * Undoes whatever credit is outstanding for a cancelled order: a hold is released, a charge is reversed.
     * Does nothing for other orders, and nothing if there is nothing outstanding, so it is safe to call twice.
     */
    @Transactional
    public void releaseOnCancellation(String actor, CustomerOrder order) {
        if (order.getPaymentMethod() != PaymentMethod.CREDIT) {
            return;
        }
        Map<CreditEntryType, BigDecimal> sums = sumsByType(entries.totalsForOrder(order.getId()));
        BigDecimal held = held(sums);
        BigDecimal charged = charged(sums);
        if (held.signum() > 0) {
            entries.save(new CreditEntry(order.getStudent(), order, CreditEntryType.RELEASE, held, actor, null));
            audit.record(actor, RELEASED, TARGET, order.getPublicId(), SUCCESS,
                    "order=" + order.getOrderNumber() + "; amount=" + held.toPlainString()
                            + "; hold released; " + PROTOTYPE);
        }
        if (charged.signum() > 0) {
            entries.save(new CreditEntry(order.getStudent(), order, CreditEntryType.REVERSAL, charged, actor, null));
            audit.record(actor, REVERSED, TARGET, order.getPublicId(), SUCCESS,
                    "order=" + order.getOrderNumber() + "; amount=" + charged.toPlainString()
                            + "; charge reversed; " + PROTOTYPE);
        }
    }

    // ---- what a student sees ----

    /** The signed-in student's own figures. A student with no account has a zero limit and nothing available. */
    @Transactional(readOnly = true)
    public CreditBalance balanceOf(String username) {
        Optional<AppUser> student = users.findByUsername(AppUser.normalize(username));
        if (student.isEmpty()) {
            return balanceFrom(false, BigDecimal.ZERO, List.of());
        }
        Optional<CreditAccount> account = accounts.findByStudent_Id(student.get().getId());
        return balanceFrom(account.isPresent(), account.map(CreditAccount::getCreditLimit).orElse(BigDecimal.ZERO),
                entries.totalsForStudent(student.get().getId()));
    }

    /** The student's newest ledger lines, for their own credit page. */
    @Transactional(readOnly = true)
    public List<CreditEntryView> recentEntries(String username) {
        List<CreditEntryView> views = new ArrayList<>();
        for (CreditEntry entry : entries.recentForStudent(AppUser.normalize(username), PageRequest.of(0, RECENT_ENTRIES))) {
            CreditEntryType type = entry.getType();
            views.add(new CreditEntryView(type.getLabel(), type.name().toLowerCase(Locale.ROOT),
                    PriceParser.format(entry.getAmount()),
                    entry.getOrder() == null ? null : entry.getOrder().getOrderNumber(),
                    timeFormat.format(entry.getCreatedAt()), entry.getNote()));
        }
        return views;
    }

    // ---- what a manager does ----

    /**
     * The students for the manager's credit page: those who already have an account, or, with some text, active
     * students whose name or username contains it.
     */
    @Transactional(readOnly = true)
    public List<CreditRow> overview(String query) {
        ManagerGuard.require();
        String text = cleanQuery(query);
        List<AppUser> students = text.isEmpty()
                ? accounts.studentsWithAccounts(PageRequest.of(0, OVERVIEW_ACCOUNTS))
                : accounts.searchStudents("%" + text.toLowerCase(Locale.ROOT) + "%",
                        PageRequest.of(0, OVERVIEW_SEARCH));
        if (students.isEmpty()) {
            return List.of();
        }
        List<Long> ids = students.stream().map(AppUser::getId).toList();
        Map<Long, CreditAccount> byStudent = new HashMap<>();
        for (CreditAccount account : accounts.findByStudentIds(ids)) {
            byStudent.put(account.getStudent().getId(), account);
        }
        Map<Long, List<Object[]>> totals = new HashMap<>();
        for (Object[] row : entries.totalsForStudents(ids)) {
            totals.computeIfAbsent((Long) row[0], id -> new ArrayList<>()).add(new Object[] {row[1], row[2]});
        }
        List<CreditRow> rows = new ArrayList<>();
        for (AppUser student : students) {
            CreditAccount account = byStudent.get(student.getId());
            CreditBalance balance = balanceFrom(account != null,
                    account == null ? BigDecimal.ZERO : account.getCreditLimit(),
                    totals.getOrDefault(student.getId(), List.of()));
            rows.add(new CreditRow(student.getPublicId(), student.getDisplayName(), student.getUsername(), balance));
        }
        return rows;
    }

    /** Sets a student's credit limit, creating their account the first time. Managers only. */
    @Transactional
    public CreditRow setLimit(String actor, String studentPublicId, String limitText) {
        ManagerGuard.require();
        AppUser student = activeStudent(studentPublicId);
        BigDecimal limit = parseAmount(limitText, true);
        if (limit.compareTo(MAX_LIMIT) > 0) {
            throw new OrderException("The credit limit can be at most " + PriceParser.format(MAX_LIMIT) + ".");
        }
        Optional<CreditAccount> existing = accounts.findForUpdate(student.getId());
        BigDecimal before = existing.map(CreditAccount::getCreditLimit).orElse(BigDecimal.ZERO);
        CreditAccount account;
        if (existing.isPresent()) {
            account = existing.get();
            account.changeLimit(limit, actor);
        } else {
            account = new CreditAccount(student, limit, actor);
            accounts.save(account);
        }
        audit.record(actor, LIMIT_SET, TARGET, student.getPublicId(), SUCCESS,
                "student=" + student.getUsername() + "; limit " + before.toPlainString() + " -> "
                        + limit.toPlainString());
        return new CreditRow(student.getPublicId(), student.getDisplayName(), student.getUsername(),
                balanceFrom(true, limit, entries.totalsForStudent(student.getId())));
    }

    /** Records that a student paid some of what they owe. It cannot be more than they owe. Managers only. */
    @Transactional
    public CreditRow settle(String actor, String studentPublicId, String amountText, String note) {
        ManagerGuard.require();
        AppUser student = activeStudent(studentPublicId);
        BigDecimal amount = parseAmount(amountText, false);
        String cleanNote = cleanNote(note);
        CreditAccount account = accounts.findForUpdate(student.getId())
                .orElseThrow(() -> new OrderException(student.getDisplayName() + " has no credit account."));
        CreditBalance before = balanceFrom(true, account.getCreditLimit(), entries.totalsForStudent(student.getId()));
        if (amount.compareTo(before.getOwed()) > 0) {
            throw new OrderException(student.getDisplayName() + " owes " + before.getOwedText()
                    + ", so a payment of " + PriceParser.format(amount) + " is too much.");
        }
        entries.save(new CreditEntry(student, null, CreditEntryType.SETTLEMENT, amount, actor, cleanNote));
        audit.record(actor, SETTLED, TARGET, student.getPublicId(), SUCCESS,
                "student=" + student.getUsername() + "; amount=" + amount.toPlainString()
                        + "; owed " + before.getOwed().toPlainString() + " -> "
                        + before.getOwed().subtract(amount).toPlainString()
                        + (cleanNote == null ? "" : "; note=" + cleanNote) + "; " + PROTOTYPE);
        return new CreditRow(student.getPublicId(), student.getDisplayName(), student.getUsername(),
                balanceFrom(true, account.getCreditLimit(), entries.totalsForStudent(student.getId())));
    }

    // ---- helpers ----

    private AppUser activeStudent(String publicId) {
        AppUser student = users.findByPublicId(publicId == null ? "" : publicId.trim())
                .filter(u -> u.hasRole("STUDENT"))
                .orElseThrow(() -> new OrderException(STUDENT_NOT_FOUND));
        if (!student.isActive()) {
            throw new OrderException(student.getDisplayName() + "'s account is not active.");
        }
        return student;
    }

    static BigDecimal parseAmount(String text, boolean zeroAllowed) {
        String trimmed = text == null ? "" : text.trim();
        if (!AMOUNT.matcher(trimmed).matches()) {
            throw new OrderException("Enter the amount in KSh as a number, for example 500 or 500.50.");
        }
        BigDecimal amount = new BigDecimal(trimmed).setScale(2);
        if (amount.signum() == 0 && !zeroAllowed) {
            throw new OrderException("Enter an amount above zero.");
        }
        return amount;
    }

    static String cleanNote(String note) {
        if (note == null) {
            return null;
        }
        String clean = note.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            return null;
        }
        if (clean.length() > CreditEntry.NOTE_MAX) {
            throw new OrderException("The note can be at most " + CreditEntry.NOTE_MAX + " characters.");
        }
        return clean;
    }

    /** Trims, removes the characters that mean something to a LIKE search, and caps the length. */
    static String cleanQuery(String query) {
        if (query == null) {
            return "";
        }
        String clean = query.replaceAll("[%_\\\\\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        return clean.length() > QUERY_MAX ? clean.substring(0, QUERY_MAX).trim() : clean;
    }

    private static CreditBalance balanceFrom(boolean hasAccount, BigDecimal limit, Collection<Object[]> totals) {
        Map<CreditEntryType, BigDecimal> sums = sumsByType(totals);
        BigDecimal owed = sums.getOrDefault(CreditEntryType.POST, BigDecimal.ZERO)
                .subtract(sums.getOrDefault(CreditEntryType.REVERSAL, BigDecimal.ZERO))
                .subtract(sums.getOrDefault(CreditEntryType.SETTLEMENT, BigDecimal.ZERO));
        return new CreditBalance(hasAccount, limit, held(sums), owed);
    }

    /** Reserved, less released, less posted. */
    private static BigDecimal held(Map<CreditEntryType, BigDecimal> sums) {
        return sums.getOrDefault(CreditEntryType.RESERVE, BigDecimal.ZERO)
                .subtract(sums.getOrDefault(CreditEntryType.RELEASE, BigDecimal.ZERO))
                .subtract(sums.getOrDefault(CreditEntryType.POST, BigDecimal.ZERO))
                .max(BigDecimal.ZERO);
    }

    /** Posted, less reversed, for one order. */
    private static BigDecimal charged(Map<CreditEntryType, BigDecimal> sums) {
        return sums.getOrDefault(CreditEntryType.POST, BigDecimal.ZERO)
                .subtract(sums.getOrDefault(CreditEntryType.REVERSAL, BigDecimal.ZERO))
                .max(BigDecimal.ZERO);
    }

    private static Map<CreditEntryType, BigDecimal> sumsByType(Collection<Object[]> totals) {
        Map<CreditEntryType, BigDecimal> sums = new EnumMap<>(CreditEntryType.class);
        for (Object[] row : totals) {
            Optional<CreditEntryType> type = CreditEntryType.parse(String.valueOf(row[0]));
            if (type.isPresent() && row[1] != null) {
                BigDecimal amount = row[1] instanceof BigDecimal b ? b : new BigDecimal(row[1].toString());
                sums.merge(type.get(), amount, BigDecimal::add);
            }
        }
        return sums;
    }
}
