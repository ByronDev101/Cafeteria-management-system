package ke.ac.kca.cafeteria.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ke.ac.kca.cafeteria.identity.AppUser;
import ke.ac.kca.cafeteria.ordering.CustomerOrder;
import ke.ac.kca.cafeteria.ordering.PaymentMethod;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentViewTest {

    private static PaymentRecord record(PaymentMethod method, PaymentStatus target) {
        CustomerOrder order = new CustomerOrder("SC-ABC123", new AppUser("amina", "hash", "Amina Wanjiru"), 5L,
                "Table 7", method, "key-12345678");
        order.addItem(1L, "Tea", new BigDecimal("50.00"), 1);
        PaymentRecord record = new PaymentRecord(order);
        switch (target) {
            case PENDING -> { }
            case VERIFIED -> record.markReceived("sam", "SJK3H2L9PQ");
            case VOIDED -> record.markVoided();
            case REFUND_DUE -> {
                record.markReceived("sam", null);
                record.markRefundDue();
            }
            case REFUNDED -> {
                record.markReceived("sam", null);
                record.markRefundDue();
                record.markRefunded();
            }
        }
        return record;
    }

    @Test
    void staffOnlyGetTheButtonThatAppliesToTheStatus() {
        for (PaymentStatus status : PaymentStatus.values()) {
            PaymentView view = PaymentView.of(record(PaymentMethod.CASH, status));
            assertEquals(status.name(), view.getStatusCode());
            assertEquals(status.getLabel(), view.getStatusLabel());
            assertEquals(status == PaymentStatus.PENDING, view.isCanVerify(), status.name());
            assertEquals(status == PaymentStatus.REFUND_DUE, view.isCanMarkRefunded(), status.name());
        }
    }

    @Test
    void theMpesaCodeBoxIsOfferedOnlyForMpesaPayments() {
        assertTrue(PaymentView.of(record(PaymentMethod.MPESA_TILL, PaymentStatus.PENDING)).isAskReference());
        assertFalse(PaymentView.of(record(PaymentMethod.CASH, PaymentStatus.PENDING)).isAskReference());
    }

    @Test
    void theReferenceAndStylingNameComeThrough() {
        PaymentView received = PaymentView.of(record(PaymentMethod.MPESA_TILL, PaymentStatus.VERIFIED));
        assertEquals("SJK3H2L9PQ", received.getReference());
        assertEquals("verified", received.getStatusClass());
        assertEquals("refund-due", PaymentView.of(record(PaymentMethod.CASH, PaymentStatus.REFUND_DUE)).getStatusClass());
    }

    @Test
    void everyStatusHasAStudentNoteThatNeverClaimsRealMoneyMoved() {
        for (PaymentStatus status : PaymentStatus.values()) {
            String note = PaymentView.of(record(PaymentMethod.CASH, status)).getStudentNote();
            assertFalse(note.isBlank(), status.name());
            String lower = note.toLowerCase();
            assertFalse(lower.contains("transferred") || lower.contains("charged") || lower.contains("deducted"),
                    status.name() + ": " + note);
        }
    }
}
