package ke.ac.kca.cafeteria.credit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CreditBalanceTest {

    private static BigDecimal kes(String amount) {
        return new BigDecimal(amount);
    }

    @Test
    void heldAndOwedBothCountAgainstTheLimit() {
        CreditBalance balance = new CreditBalance(true, kes("500.00"), kes("100.00"), kes("150.00"));

        assertEquals(kes("250.00"), balance.getUsed());
        assertEquals(kes("250.00"), balance.getAvailable());
        assertTrue(balance.isCanUse());
        assertEquals("KSh 500.00", balance.getLimitText());
        assertEquals("KSh 100.00", balance.getHeldText());
        assertEquals("KSh 150.00", balance.getOwedText());
        assertEquals("KSh 250.00", balance.getAvailableText());
    }

    @Test
    void nothingIsAvailableOnceTheLimitIsUsedOrLoweredBelowWhatIsUsed() {
        CreditBalance full = new CreditBalance(true, kes("200.00"), kes("50.00"), kes("150.00"));
        assertEquals(kes("0.00"), full.getAvailable());
        assertFalse(full.isCanUse());

        CreditBalance lowered = new CreditBalance(true, kes("100.00"), kes("50.00"), kes("150.00"));
        assertEquals(kes("0.00"), lowered.getAvailable());
        assertEquals("KSh 0.00", lowered.getAvailableText());
        assertFalse(lowered.isCanUse());
    }

    @Test
    void aStudentWithNoAccountCannotUseCreditEvenWithANonZeroLimitFigure() {
        CreditBalance none = new CreditBalance(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertFalse(none.isHasAccount());
        assertFalse(none.isCanUse());
        assertEquals("KSh 0.00", none.getAvailableText());

        assertFalse(new CreditBalance(false, kes("300.00"), BigDecimal.ZERO, BigDecimal.ZERO).isCanUse());
    }

    @Test
    void negativeFiguresFromOddLedgerHistoryAreShownAsZero() {
        CreditBalance balance = new CreditBalance(true, kes("300.00"), kes("-20.00"), kes("-5.00"));

        assertEquals(kes("0.00"), balance.getHeld().setScale(2));
        assertEquals(kes("0.00"), balance.getOwed().setScale(2));
        assertEquals(kes("300.00"), balance.getAvailable());
    }
}
