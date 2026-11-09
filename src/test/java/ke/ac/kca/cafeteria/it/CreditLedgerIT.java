package ke.ac.kca.cafeteria.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ke.ac.kca.cafeteria.credit.CreditEntryView;
import ke.ac.kca.cafeteria.credit.CreditRow;
import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.ordering.OrderException;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.ordering.PlacedOrder;
import ke.ac.kca.cafeteria.reporting.ManagerReport;
import ke.ac.kca.cafeteria.reporting.ReportLine;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Controlled student credit end to end on a real database: holds, charges, reversals, payments and refusals. */
class CreditLedgerIT extends DatabaseIT {

    private void assertBalance(String username, String held, String owed, String available) {
        assertEquals(held, balanceOf(username).getHeldText(), "held");
        assertEquals(owed, balanceOf(username).getOwedText(), "owed");
        assertEquals(available, balanceOf(username).getAvailableText(), "available");
    }

    @Test
    void holdChargeReversalAndPaymentWorkInOrder() {
        AppUser amina = student("amina");
        setCreditLimit(amina, "500");

        PlacedOrder first = place("amina", PaymentMethod.CREDIT, 2);                     // KSh 100.00
        assertBalance("amina", "KSh 100.00", "KSh 0.00", "KSh 400.00");
        assertEquals("PENDING", paymentStatus(first.publicId()));

        signInAs("sam", "ROLE_STAFF");
        processing.transition("sam", first.publicId(), "ACCEPTED", null, null);
        assertBalance("amina", "KSh 0.00", "KSh 100.00", "KSh 400.00");
        assertEquals("VERIFIED", paymentStatus(first.publicId()));

        processing.transition("sam", first.publicId(), "CANCELLED", "Out of stock", null);
        assertBalance("amina", "KSh 0.00", "KSh 0.00", "KSh 500.00");
        assertEquals("REFUNDED", paymentStatus(first.publicId()));
        assertEquals(0L, paymentService.refundsDueCount());           // nobody hands cash back for a credit charge

        PlacedOrder second = place("amina", PaymentMethod.CREDIT, 3);                    // KSh 150.00
        signInAs("sam", "ROLE_STAFF");
        processing.transition("sam", second.publicId(), "ACCEPTED", null, null);
        assertBalance("amina", "KSh 0.00", "KSh 150.00", "KSh 350.00");

        signInAs("pat", "ROLE_MANAGER");
        CreditRow afterPayment = creditService.settle("pat", amina.getPublicId(), "60", "Paid at the till");
        assertEquals("KSh 90.00", afterPayment.getBalance().getOwedText());
        assertBalance("amina", "KSh 0.00", "KSh 90.00", "KSh 410.00");

        // the ledger only ever grew: reserve, post, reverse for the first order; reserve, post, settle for the second
        assertEquals(1, auditCount("CREDIT_LIMIT_SET"));
        assertEquals(2, auditCount("CREDIT_RESERVED"));
        assertEquals(2, auditCount("CREDIT_POSTED"));
        assertEquals(1, auditCount("CREDIT_REVERSED"));
        assertEquals(1, auditCount("CREDIT_SETTLED"));
        assertEquals(6, count("credit_entries"));
        assertEquals(0, new BigDecimal("250.00").compareTo(sumOf("RESERVE")));
        assertEquals(0, new BigDecimal("250.00").compareTo(sumOf("POST")));
        assertEquals(0, new BigDecimal("100.00").compareTo(sumOf("REVERSAL")));
        assertEquals(0, new BigDecimal("60.00").compareTo(sumOf("SETTLEMENT")));
        assertEquals(0, new BigDecimal("0").compareTo(sumOf("RELEASE")));
    }

    private BigDecimal sumOf(String type) {
        BigDecimal total = jdbc.queryForObject("select coalesce(sum(amount), 0) from credit_entries where entry_type = ?",
                BigDecimal.class, type);
        return total == null ? BigDecimal.ZERO : total;
    }

    @Test
    void anOrderOverTheLimitLeavesNoTraceInTheDatabase() {
        AppUser amina = student("amina");
        setCreditLimit(amina, "100");

        OrderException e = assertThrows(OrderException.class, () -> place("amina", PaymentMethod.CREDIT, 3));   // 150 > 100

        assertTrue(e.getMessage().contains("KSh 150.00") && e.getMessage().contains("KSh 100.00"), e.getMessage());
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_items"));
        assertEquals(0, count("order_status_history"));
        assertEquals(0, count("payment_records"));
        assertEquals(0, count("credit_entries"));
        assertEquals(0, auditCount("ORDER_PLACED"));
        assertBalance("amina", "KSh 0.00", "KSh 0.00", "KSh 100.00");
    }

    @Test
    void aStudentWithNoCreditAccountIsRefusedAndNothingIsSaved() {
        student("amina");

        assertThrows(OrderException.class, () -> place("amina", PaymentMethod.CREDIT, 1));

        assertEquals(0, count("orders"));
        assertEquals(0, count("payment_records"));
        assertEquals(0, count("credit_entries"));
    }

    @Test
    void anOrderExactlyAtTheLimitIsAllowedAndTheNextIsRefused() {
        AppUser amina = student("amina");
        setCreditLimit(amina, "100");

        place("amina", PaymentMethod.CREDIT, 2);
        assertBalance("amina", "KSh 100.00", "KSh 0.00", "KSh 0.00");

        assertThrows(OrderException.class, () -> place("amina", PaymentMethod.CREDIT, 1));
        assertEquals(1, count("orders"));
        assertEquals(1, count("credit_entries"));
    }

    @Test
    void aStudentCancellingBeforeAcceptanceReleasesTheHold() {
        AppUser amina = student("amina");
        setCreditLimit(amina, "500");
        PlacedOrder placed = place("amina", PaymentMethod.CREDIT, 2);
        assertBalance("amina", "KSh 100.00", "KSh 0.00", "KSh 400.00");

        orderService.cancelOwn("amina", placed.publicId());

        assertBalance("amina", "KSh 0.00", "KSh 0.00", "KSh 500.00");
        assertEquals("VOIDED", paymentStatus(placed.publicId()));
        assertEquals(1, auditCount("CREDIT_RELEASED"));
        assertEquals(2, count("credit_entries"));                      // the hold and its release; nothing was edited
    }

    @Test
    void acceptingAnOrderWhoseCreditIsNotHeldIsRefusedAndNothingChanges() {
        AppUser amina = student("amina");
        setCreditLimit(amina, "500");
        PlacedOrder placed = place("amina", PaymentMethod.CREDIT, 1);
        jdbc.update("delete from credit_entries");                    // simulate a missing hold

        signInAs("sam", "ROLE_STAFF");
        OrderException e = assertThrows(OrderException.class,
                () -> processing.transition("sam", placed.publicId(), "ACCEPTED", null, null));

        assertTrue(e.getMessage().contains("No credit is held"), e.getMessage());
        assertEquals("SUBMITTED", orderStatus(placed.publicId()));
        assertEquals("PENDING", paymentStatus(placed.publicId()));
        assertEquals(0, count("credit_entries"));
    }

    @Test
    void aPaymentCannotBeMoreThanWhatIsOwed() {
        AppUser amina = student("amina");
        setCreditLimit(amina, "500");
        PlacedOrder placed = place("amina", PaymentMethod.CREDIT, 2);
        signInAs("sam", "ROLE_STAFF");
        processing.transition("sam", placed.publicId(), "ACCEPTED", null, null);

        signInAs("pat", "ROLE_MANAGER");
        assertThrows(OrderException.class, () -> creditService.settle("pat", amina.getPublicId(), "100.01", null));
        assertBalance("amina", "KSh 0.00", "KSh 100.00", "KSh 400.00");

        creditService.settle("pat", amina.getPublicId(), "100", null);
        assertBalance("amina", "KSh 0.00", "KSh 0.00", "KSh 500.00");
    }

    @Test
    void loweringTheLimitBelowWhatIsUsedBlocksNewOrdersButKeepsExistingOnes() {
        AppUser amina = student("amina");
        setCreditLimit(amina, "500");
        place("amina", PaymentMethod.CREDIT, 4);                      // KSh 200.00 held

        setCreditLimit(amina, "150");

        assertBalance("amina", "KSh 200.00", "KSh 0.00", "KSh 0.00");
        assertThrows(OrderException.class, () -> place("amina", PaymentMethod.CREDIT, 1));
        assertEquals(1, count("orders"));
    }

    @Test
    void theStudentSeesTheirOwnHistoryNewestFirst() {
        AppUser amina = student("amina");
        student("brian");
        setCreditLimit(amina, "500");
        PlacedOrder placed = place("amina", PaymentMethod.CREDIT, 1);
        orderService.cancelOwn("amina", placed.publicId());

        List<CreditEntryView> entries = creditService.recentEntries("amina");

        assertEquals(2, entries.size());
        assertEquals("Hold released", entries.get(0).getTypeLabel());
        assertEquals("Held for an order", entries.get(1).getTypeLabel());
        assertEquals(placed.orderNumber(), entries.get(1).getOrderNumber());
        assertTrue(creditService.recentEntries("brian").isEmpty());
    }

    @Test
    void theManagersReportSummarisesTheLedgerAndWhatIsOutstanding() {
        AppUser amina = student("amina");
        setCreditLimit(amina, "500");
        PlacedOrder charged = place("amina", PaymentMethod.CREDIT, 2);        // 100 charged
        place("amina", PaymentMethod.CREDIT, 1);                              // 50 still held
        signInAs("sam", "ROLE_STAFF");
        processing.transition("sam", charged.publicId(), "ACCEPTED", null, null);
        signInAs("pat", "ROLE_MANAGER");
        creditService.settle("pat", amina.getPublicId(), "30", null);

        ManagerReport report = reportService.build(null, null, null, null, null);

        assertEquals("KSh 70.00", report.getCreditOwedText());
        assertEquals("KSh 50.00", report.getCreditHeldText());
        ReportLine held = lineOf(report, "Held for an order");
        assertEquals(2, held.getCount());
        assertEquals("KSh 150.00", held.getAmountText());
        assertEquals("KSh 100.00", lineOf(report, "Charged to your credit").getAmountText());
        assertEquals("KSh 30.00", lineOf(report, "Payment recorded").getAmountText());
        assertEquals(0, lineOf(report, "Charge cancelled").getCount());
    }

    private static ReportLine lineOf(ManagerReport report, String label) {
        return report.getCreditLines().stream().filter(l -> l.getLabel().equals(label)).findFirst().orElseThrow();
    }
}
