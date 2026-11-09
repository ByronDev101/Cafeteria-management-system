package ke.ac.kca.cafeteria.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.OrderException;
import ke.ac.kca.cafeteria.ordering.OrderView;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import ke.ac.kca.cafeteria.ordering.PlacedOrder;
import ke.ac.kca.cafeteria.ordering.QueueFilter;
import ke.ac.kca.cafeteria.ordering.QueueOrder;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Payment records and refunds through the real services, transactions and queries. */
class PaymentFlowIT extends DatabaseIT {

    @Test
    void aPaymentIsOpenedWithTheOrderAndCopiesItsAmount() {
        student("amina");

        PlacedOrder placed = place("amina", PaymentMethod.MPESA_TILL, 2);

        assertEquals(1, count("payment_records"));
        assertEquals("PENDING", paymentStatus(placed.publicId()));
        assertEquals(0, new BigDecimal("100.00").compareTo(sum("amount", "payment_records")));
        assertEquals("MPESA_TILL", jdbc.queryForObject("select method_code from payment_records", String.class));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("select prototype from payment_records", Boolean.class));
        assertEquals(1, auditCount("PAYMENT_OPENED"));
    }

    @Test
    void aPaymentIsReceivedThenRefundedAfterAStaffCancellation() {
        student("amina");
        PlacedOrder placed = place("amina", PaymentMethod.MPESA_TILL, 2);

        signInAs("sam", "ROLE_STAFF");
        paymentService.verify("sam", placed.publicId(), "SJK3H2L9PQ");
        assertEquals("VERIFIED", paymentStatus(placed.publicId()));
        assertEquals("sam", jdbc.queryForObject("select verified_by from payment_records", String.class));
        assertEquals("SJK3H2L9PQ", jdbc.queryForObject("select reference from payment_records", String.class));

        processing.transition("sam", placed.publicId(), "CANCELLED", "Out of stock", null);
        assertEquals("CANCELLED", orderStatus(placed.publicId()));
        assertEquals("REFUND_DUE", paymentStatus(placed.publicId()));

        List<CustomerOrder> due = paymentService.refundsDue(100);
        assertEquals(1, due.size());
        assertEquals(placed.publicId(), due.get(0).getPublicId());
        assertEquals(1L, paymentService.refundsDueCount());

        paymentService.markRefunded("sam", placed.publicId());
        assertEquals("REFUNDED", paymentStatus(placed.publicId()));
        assertEquals(0L, paymentService.refundsDueCount());

        assertEquals(1, auditCount("PAYMENT_VERIFIED"));
        assertEquals(1, auditCount("PAYMENT_REFUND_DUE"));
        assertEquals(1, auditCount("PAYMENT_REFUNDED"));
    }

    @Test
    void aStudentCancellingBeforePaymentVoidsTheRecord() {
        student("amina");
        PlacedOrder placed = place("amina", PaymentMethod.CASH, 1);

        orderService.cancelOwn("amina", placed.publicId());

        assertEquals("CANCELLED", orderStatus(placed.publicId()));
        assertEquals("VOIDED", paymentStatus(placed.publicId()));
        assertEquals(1, auditCount("PAYMENT_VOIDED"));
    }

    @Test
    void aPaymentCannotBeReceivedTwiceOrForACancelledOrder() {
        student("amina");
        PlacedOrder first = place("amina", PaymentMethod.CASH, 1);
        PlacedOrder second = place("amina", PaymentMethod.CASH, 1);

        signInAs("sam", "ROLE_STAFF");
        paymentService.verify("sam", first.publicId(), null);
        OrderException again = assertThrows(OrderException.class,
                () -> paymentService.verify("sam", first.publicId(), null));
        assertTrue(again.getMessage().contains("already marked as received"));

        processing.transition("sam", second.publicId(), "CANCELLED", "Customer left", null);
        assertThrows(OrderException.class, () -> paymentService.verify("sam", second.publicId(), null));

        assertEquals("VERIFIED", paymentStatus(first.publicId()));
        assertEquals("VOIDED", paymentStatus(second.publicId()));
        assertEquals(1, auditCount("PAYMENT_VERIFIED"));
    }

    @Test
    void theBoardAndTheStudentsOrderPageShowThePayment() {
        student("amina");
        PlacedOrder placed = place("amina", PaymentMethod.CASH, 1);

        signInAs("sam", "ROLE_STAFF");
        List<QueueOrder> board = processing.queue(QueueFilter.ACTIVE);
        assertEquals(1, board.size());
        assertEquals("Awaiting payment", board.get(0).getPaymentStatusLabel());
        assertTrue(board.get(0).isCanVerifyPayment());

        OrderView view = orderService.detail("amina", placed.publicId());
        assertEquals("PENDING", view.getPaymentStatusCode());
        assertNotNull(view.getPaymentNote());

        paymentService.verify("sam", placed.publicId(), null);
        processing.transition("sam", placed.publicId(), "CANCELLED", "Out of stock", null);

        List<QueueOrder> refunds = processing.queue(QueueFilter.REFUNDS);
        assertEquals(1, refunds.size());
        assertTrue(refunds.get(0).isCanMarkRefunded());
        assertEquals(1L, processing.counts().get(QueueFilter.REFUNDS));
    }

    @Test
    void thePollTokenChangesWhenAPaymentChanges() {
        student("amina");
        PlacedOrder placed = place("amina", PaymentMethod.CASH, 1);
        signInAs("sam", "ROLE_STAFF");
        String before = processing.pollToken();

        paymentService.verify("sam", placed.publicId(), null);

        assertTrue(!before.equals(processing.pollToken()));
    }
}
