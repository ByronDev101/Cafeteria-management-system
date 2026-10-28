package ke.ac.kca.cafeteria.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.OrderStatus;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentRecordTest {

    private static CustomerOrder order(PaymentMethod method) {
        CustomerOrder order = new CustomerOrder("SC-ABC123", new AppUser("amina", "hash", "Amina Wanjiru"), 5L,
                "Table 7", method, "key-12345678");
        order.addItem(1L, "Tea", new BigDecimal("50.00"), 2);
        order.addItem(2L, "Samosa", new BigDecimal("30.00"), 1);
        return order;
    }

    @Test
    void aNewRecordIsAwaitingPaymentAndLabelledAsAPrototype() {
        PaymentRecord record = new PaymentRecord(order(PaymentMethod.MPESA_TILL));

        assertEquals(PaymentStatus.PENDING, record.getStatus());
        assertEquals(new BigDecimal("130.00"), record.getAmount());
        assertEquals("MPESA_TILL", record.getMethodCode());
        assertEquals(PaymentMethod.MPESA_TILL, record.getPaymentMethod());
        assertTrue(record.isPrototype());
        assertNotNull(record.getPublicId());
        assertNull(record.getReference());
        assertNull(record.getVerifiedBy());
        assertNull(record.getVerifiedAt());
    }

    @Test
    void aRecordForAnOrderThatIsAlreadyCancelledStartsVoided() {
        CustomerOrder cancelled = order(PaymentMethod.CASH);
        cancelled.moveTo(OrderStatus.CANCELLED, "amina", "Cancelled by the student");

        assertEquals(PaymentStatus.VOIDED, new PaymentRecord(cancelled).getStatus());
    }

    @Test
    void receivingAPaymentRecordsWhoAndWhen() {
        PaymentRecord record = new PaymentRecord(order(PaymentMethod.MPESA_TILL));

        record.markReceived("sam", "SJK3H2L9PQ");

        assertEquals(PaymentStatus.VERIFIED, record.getStatus());
        assertEquals("sam", record.getVerifiedBy());
        assertEquals("SJK3H2L9PQ", record.getReference());
        assertNotNull(record.getVerifiedAt());
    }

    @Test
    void theWholeRefundPathWorksInOrder() {
        PaymentRecord record = new PaymentRecord(order(PaymentMethod.CASH));

        record.markReceived("sam", null);
        record.markRefundDue();
        assertEquals(PaymentStatus.REFUND_DUE, record.getStatus());
        record.markRefunded();
        assertEquals(PaymentStatus.REFUNDED, record.getStatus());
    }

    @Test
    void aMoveThatIsNotAllowedIsRefusedAndChangesNothing() {
        PaymentRecord pending = new PaymentRecord(order(PaymentMethod.CASH));
        assertThrows(IllegalStateException.class, pending::markRefundDue);
        assertThrows(IllegalStateException.class, pending::markRefunded);
        assertEquals(PaymentStatus.PENDING, pending.getStatus());

        PaymentRecord voided = new PaymentRecord(order(PaymentMethod.CASH));
        voided.markVoided();
        assertThrows(IllegalStateException.class, () -> voided.markReceived("sam", "REF"));
        assertThrows(IllegalStateException.class, voided::markRefundDue);
        assertEquals(PaymentStatus.VOIDED, voided.getStatus());
        assertNull(voided.getReference());
        assertNull(voided.getVerifiedBy());

        PaymentRecord received = new PaymentRecord(order(PaymentMethod.CASH));
        received.markReceived("sam", null);
        assertThrows(IllegalStateException.class, () -> received.markReceived("sam", "AGAIN"));
        assertThrows(IllegalStateException.class, received::markVoided);
        assertThrows(IllegalStateException.class, received::markRefunded);
        assertEquals(PaymentStatus.VERIFIED, received.getStatus());
        assertNull(received.getReference());
    }
}
