package ke.ac.kca.cafeteria.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaymentStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "PENDING>VERIFIED", "PENDING>VOIDED", "VERIFIED>REFUND_DUE", "REFUND_DUE>REFUNDED");

    @Test
    void onlyTheAgreedMovesAreAllowed() {
        for (PaymentStatus from : PaymentStatus.values()) {
            for (PaymentStatus to : PaymentStatus.values()) {
                boolean expected = ALLOWED.contains(from + ">" + to);
                assertEquals(expected, from.canMoveTo(to), from + " to " + to);
            }
        }
    }

    @Test
    void voidedAndRefundedAreFinal() {
        for (PaymentStatus to : PaymentStatus.values()) {
            assertFalse(PaymentStatus.VOIDED.canMoveTo(to));
            assertFalse(PaymentStatus.REFUNDED.canMoveTo(to));
        }
    }

    @Test
    void everyStatusHasAPlainLabelAndCanBeParsed() {
        for (PaymentStatus status : PaymentStatus.values()) {
            assertFalse(status.getLabel().isBlank());
            assertEquals(status.name(), status.getCode());
            assertEquals(Optional.of(status), PaymentStatus.parse(" " + status.name() + " "));
        }
        assertTrue(PaymentStatus.parse("pending").isEmpty());
        assertTrue(PaymentStatus.parse("' OR 1=1 --").isEmpty());
        assertTrue(PaymentStatus.parse(null).isEmpty());
    }
}
