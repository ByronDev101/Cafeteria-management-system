package ke.ac.kca.cafeteria.reporting;

import ke.ac.kca.cafeteria.finance.PaymentStatus;
import ke.ac.kca.cafeteria.finance.PaymentService;
import ke.ac.kca.cafeteria.finance.PaymentView;
import ke.ac.kca.cafeteria.menu.PriceParser;
import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.OrderStatus;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The manager reports (FR-033 to FR-035): orders by status, payment totals, and a search over orders.
 *
 * <p>Managers only, checked here as well as in the URL rules. Everything is read-only. Dates are campus time, and a
 * range that cannot be used falls back to the last 7 days with a warning. The search is limited to 100 orders and
 * says when more matched. Payment figures are prototype records written down by staff, not real money.
 */
@Service
public class ReportService {

    public static final int SEARCH_LIMIT = 100;
    private static final int TEXT_MAX = 40;

    private final ReportRepository reports;
    private final PaymentService payments;
    private final ZoneId zone;
    private final DateTimeFormatter timeFormat;

    public ReportService(ReportRepository reports, PaymentService payments,
                         @Value("${app.display-timezone:Africa/Nairobi}") String zoneId) {
        this.reports = reports;
        this.payments = payments;
        this.zone = ZoneId.of(zoneId);
        this.timeFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(zone);
    }

    @Transactional(readOnly = true)
    public ManagerReport build(String fromText, String toText, String statusCode, String numberText, String tableText) {
        ManagerGuard.require();
        ReportRange range = ReportRange.parse(fromText, toText, zone, Clock.system(zone));
        List<String> warnings = new ArrayList<>(range.getWarnings());

        // ---- orders by status ----
        Map<String, Object[]> orderRows = new HashMap<>();
        for (Object[] row : reports.orderTotalsByStatus(range.start(), range.endExclusive())) {
            orderRows.put(String.valueOf(row[0]), row);
        }
        List<ReportLine> orderLines = new ArrayList<>();
        long orderCount = 0;
        for (OrderStatus status : OrderStatus.values()) {
            Object[] row = orderRows.get(status.name());
            long count = row == null ? 0L : ((Number) row[1]).longValue();
            BigDecimal amount = row == null ? BigDecimal.ZERO : money(row[2]);
            orderCount += count;
            orderLines.add(new ReportLine(status.getLabel(), "status-" + status.name().toLowerCase(Locale.ROOT),
                    count, PriceParser.format(amount)));
        }

        // ---- payment totals ----
        Map<PaymentStatus, long[]> paymentCounts = new EnumMap<>(PaymentStatus.class);
        Map<PaymentStatus, BigDecimal> paymentSums = new EnumMap<>(PaymentStatus.class);
        Map<String, long[]> receivedCounts = new HashMap<>();
        Map<String, BigDecimal> receivedSums = new HashMap<>();
        for (Object[] row : reports.paymentTotals(range.start(), range.endExclusive())) {
            Optional<PaymentStatus> status = PaymentStatus.parse(String.valueOf(row[0]));
            if (status.isEmpty()) {
                continue;
            }
            String method = String.valueOf(row[1]);
            long count = ((Number) row[2]).longValue();
            BigDecimal amount = money(row[3]);
            paymentCounts.computeIfAbsent(status.get(), s -> new long[1])[0] += count;
            paymentSums.merge(status.get(), amount, BigDecimal::add);
            if (status.get() == PaymentStatus.VERIFIED) {
                receivedCounts.computeIfAbsent(method, m -> new long[1])[0] += count;
                receivedSums.merge(method, amount, BigDecimal::add);
            }
        }
        List<ReportLine> paymentLines = new ArrayList<>();
        for (PaymentStatus status : PaymentStatus.values()) {
            long[] count = paymentCounts.get(status);
            BigDecimal amount = paymentSums.getOrDefault(status, BigDecimal.ZERO);
            paymentLines.add(new ReportLine(status.getLabel(),
                    "pay-" + status.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                    count == null ? 0L : count[0], PriceParser.format(amount)));
        }
        List<ReportLine> methodLines = new ArrayList<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            long[] count = receivedCounts.get(method.name());
            if (count == null && !PaymentMethod.offered().contains(method)) {
                continue;
            }
            methodLines.add(new ReportLine(method.getLabel(), "", count == null ? 0L : count[0],
                    PriceParser.format(receivedSums.getOrDefault(method.name(), BigDecimal.ZERO))));
        }

        // ---- search ----
        String chosenStatus = "";
        List<String> statuses = Arrays.stream(OrderStatus.values()).map(Enum::name).toList();
        if (statusCode != null && !statusCode.isBlank()) {
            Optional<OrderStatus> parsed = parseStatus(statusCode);
            if (parsed.isPresent()) {
                chosenStatus = parsed.get().name();
                statuses = List.of(chosenStatus);
            } else {
                warnings.add("That status was not recognised, so all statuses are shown.");
            }
        }
        String number = cleanText(numberText);
        String table = cleanText(tableText);
        List<CustomerOrder> found = new ArrayList<>(reports.search(range.start(), range.endExclusive(), statuses,
                like(number), like(table), PageRequest.of(0, SEARCH_LIMIT + 1)));
        boolean more = found.size() > SEARCH_LIMIT;
        if (more) {
            found = new ArrayList<>(found.subList(0, SEARCH_LIMIT));
        }
        Map<Long, PaymentView> views = payments.viewsFor(found);
        List<ReportRow> rows = new ArrayList<>();
        for (CustomerOrder order : found) {
            PaymentView payment = order.getId() == null ? null : views.get(order.getId());
            OrderStatus status = order.getStatus();
            rows.add(new ReportRow(order.getOrderNumber(), timeFormat.format(order.getCreatedAt()),
                    order.getTableLabel(), status.getLabel(), "status-" + status.name().toLowerCase(Locale.ROOT),
                    PriceParser.format(order.getTotal()), order.getPaymentMethod().getLabel(),
                    payment == null ? null : payment.getStatusLabel(),
                    payment == null ? "" : "pay-" + payment.getStatusClass()));
        }

        return new ManagerReport(range.getFromText(), range.getToText(), warnings, orderCount, orderLines,
                paymentLines, methodLines, chosenStatus, number, table, rows, more, SEARCH_LIMIT);
    }

    // ---- helpers ----

    private static Optional<OrderStatus> parseStatus(String code) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.name().equals(code.trim())) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }

    /** Trims, removes the characters that mean something to a LIKE search, and caps the length. */
    static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("[%_\\\\\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        return clean.length() > TEXT_MAX ? clean.substring(0, TEXT_MAX).trim() : clean;
    }

    /** The lower-case LIKE pattern for a cleaned search text. Empty text matches everything. */
    static String like(String cleaned) {
        return "%" + cleaned.toLowerCase(Locale.ROOT) + "%";
    }

    private static BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal amount ? amount : new BigDecimal(value.toString());
    }
}
