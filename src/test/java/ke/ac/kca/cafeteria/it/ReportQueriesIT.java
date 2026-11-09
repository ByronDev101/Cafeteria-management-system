package ke.ac.kca.cafeteria.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.ordering.PlacedOrder;
import ke.ac.kca.cafeteria.reporting.ManagerReport;
import ke.ac.kca.cafeteria.reporting.ReportLine;
import ke.ac.kca.cafeteria.reporting.ReportRow;
import ke.ac.kca.cafeteria.reporting.ReportService;
import ke.ac.kca.cafeteria.table.CafeteriaTable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The manager report queries against real data, including the campus-day boundaries (Nairobi is UTC+3). */
class ReportQueriesIT extends DatabaseIT {

    private static ReportLine line(Iterable<ReportLine> lines, String label) {
        for (ReportLine line : lines) {
            if (line.getLabel().equals(label)) {
                return line;
            }
        }
        throw new AssertionError("No report line called " + label);
    }

    private PlacedOrder placeAt(String username, PaymentMethod method, int teas, Instant createdAt) {
        PlacedOrder placed = place(username, method, teas);
        backdate(placed.publicId(), createdAt);
        return placed;
    }

    @Test
    void ordersAndPaymentsAreTotalledForTheChosenCampusDays() {
        student("amina");
        // 23:59:59 on 27 Oct in Nairobi, then midnight and mid-morning on 28 Oct
        PlacedOrder lateOn27th = placeAt("amina", PaymentMethod.CASH, 1, Instant.parse("2026-10-27T20:59:59Z"));
        PlacedOrder midnightOn28th = placeAt("amina", PaymentMethod.CASH, 2, Instant.parse("2026-10-27T21:00:00Z"));
        PlacedOrder morningOn28th = placeAt("amina", PaymentMethod.MPESA_TILL, 3, Instant.parse("2026-10-28T07:00:00Z"));

        signInAs("sam", "ROLE_STAFF");
        paymentService.verify("sam", midnightOn28th.publicId(), null);
        paymentService.verify("sam", morningOn28th.publicId(), "SJK3H2L9PQ");
        processing.transition("sam", morningOn28th.publicId(), "CANCELLED", "Out of stock", null);

        signInAs("pat", "ROLE_MANAGER");
        ManagerReport day28 = reportService.build("2026-10-28", "2026-10-28", null, null, null);
        assertEquals(2, day28.getOrderCount());
        assertEquals(1, line(day28.getOrderLines(), "Submitted").getCount());
        assertEquals("KSh 100.00", line(day28.getOrderLines(), "Submitted").getAmountText());
        assertEquals(1, line(day28.getOrderLines(), "Cancelled").getCount());
        assertEquals("KSh 150.00", line(day28.getOrderLines(), "Cancelled").getAmountText());
        assertEquals(1, line(day28.getPaymentLines(), "Payment received").getCount());
        assertEquals("KSh 100.00", line(day28.getPaymentLines(), "Payment received").getAmountText());
        assertEquals(1, line(day28.getPaymentLines(), "Refund due").getCount());
        assertEquals("KSh 150.00", line(day28.getPaymentLines(), "Refund due").getAmountText());
        assertEquals(0, line(day28.getPaymentLines(), "Awaiting payment").getCount());
        assertEquals(1, line(day28.getMethodLines(), "Cash").getCount());
        assertEquals("KSh 100.00", line(day28.getMethodLines(), "Cash").getAmountText());

        ManagerReport day27 = reportService.build("2026-10-27", "2026-10-27", null, null, null);
        assertEquals(1, day27.getOrderCount());
        assertEquals(1, line(day27.getPaymentLines(), "Awaiting payment").getCount());
        assertEquals("KSh 50.00", line(day27.getPaymentLines(), "Awaiting payment").getAmountText());
        assertEquals(1, day27.getRows().size());
        assertEquals(lateOn27th.orderNumber(), day27.getRows().get(0).getOrderNumber());

        ManagerReport both = reportService.build("2026-10-27", "2026-10-28", null, null, null);
        assertEquals(3, both.getOrderCount());
    }

    @Test
    void theSearchFiltersByStatusOrderNumberAndTableAndListsNewestFirst() {
        student("amina");
        CafeteriaTable patio = tableRepository.save(new CafeteriaTable("Patio 2", "b".repeat(64)));
        PlacedOrder older = placeAt("amina", PaymentMethod.CASH, 1, Instant.parse("2026-10-28T06:00:00Z"));
        PlacedOrder newer = orderService.place("amina", Map.of(tea.getPublicId(), 1), patio.getPublicId(),
                PaymentMethod.CASH, UUID.randomUUID().toString());
        backdate(newer.publicId(), Instant.parse("2026-10-28T08:00:00Z"));
        signInAs("sam", "ROLE_STAFF");
        processing.transition("sam", older.publicId(), "CANCELLED", "Out of stock", null);

        signInAs("pat", "ROLE_MANAGER");
        ManagerReport all = reportService.build("2026-10-28", "2026-10-28", null, null, null);
        assertEquals(2, all.getRows().size());
        assertEquals(newer.orderNumber(), all.getRows().get(0).getOrderNumber());
        assertEquals(older.orderNumber(), all.getRows().get(1).getOrderNumber());

        ManagerReport cancelled = reportService.build("2026-10-28", "2026-10-28", "CANCELLED", null, null);
        assertEquals(1, cancelled.getRows().size());
        assertEquals(older.orderNumber(), cancelled.getRows().get(0).getOrderNumber());
        assertEquals("No payment taken", cancelled.getRows().get(0).getPaymentLabel());

        ManagerReport byTable = reportService.build("2026-10-28", "2026-10-28", null, null, "patio");
        assertEquals(1, byTable.getRows().size());
        assertEquals("Patio 2", byTable.getRows().get(0).getTableLabel());

        String fragment = older.orderNumber().substring(older.orderNumber().length() - 4).toLowerCase();
        ManagerReport byNumber = reportService.build("2026-10-28", "2026-10-28", null, fragment, null);
        assertTrue(byNumber.getRows().stream().anyMatch(r -> r.getOrderNumber().equals(older.orderNumber())));

        ManagerReport none = reportService.build("2026-10-28", "2026-10-28", null, "no-such-number", null);
        assertTrue(none.getRows().isEmpty());
        assertFalse(none.isMoreMatches());
    }

    @Test
    void theSearchStopsAtOneHundredAndSaysThereAreMore() {
        student("amina");
        for (int i = 0; i < ReportService.SEARCH_LIMIT + 1; i++) {
            place("amina", PaymentMethod.CASH, 1);
        }
        signInAs("pat", "ROLE_MANAGER");

        ManagerReport report = reportService.build(null, null, null, null, null);

        assertEquals(ReportService.SEARCH_LIMIT, report.getRows().size());
        assertTrue(report.isMoreMatches());
        assertEquals(ReportService.SEARCH_LIMIT + 1L, report.getOrderCount());
        ReportRow first = report.getRows().get(0);
        assertEquals("Awaiting payment", first.getPaymentLabel());
    }

    @Test
    void searchTextThatLooksLikeAWildcardOrSqlIsJustText() {
        student("amina");
        place("amina", PaymentMethod.CASH, 1);
        signInAs("pat", "ROLE_MANAGER");

        ManagerReport injection = reportService.build(null, null, null, "' OR '1'='1", "x'; DROP TABLE orders; --");
        assertTrue(injection.getRows().isEmpty());
        assertEquals(1, count("orders"));

        ManagerReport percent = reportService.build(null, null, null, "%", "_");
        assertEquals(1, percent.getRows().size());
    }
}
