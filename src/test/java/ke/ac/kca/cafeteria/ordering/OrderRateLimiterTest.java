package ke.ac.kca.cafeteria.ordering;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderRateLimiterTest {

    @Test
    void aStudentIsAllowedUpToTheLimitThenAskedToWait() {
        OrderRateLimiter limiter = new OrderRateLimiter(2, 10);
        limiter.ensureAllowed("amina");
        limiter.recordPlaced("amina");
        limiter.ensureAllowed("amina");
        limiter.recordPlaced("amina");

        OrderException e = assertThrows(OrderException.class, () -> limiter.ensureAllowed("amina"));

        assertTrue(e.getMessage().contains("several orders in a short time"));
        assertTrue(e.getMessage().contains("10 minutes"), e.getMessage());
    }

    @Test
    void checkingAloneNeverUsesUpTheLimit() {
        OrderRateLimiter limiter = new OrderRateLimiter(1, 10);
        for (int i = 0; i < 20; i++) {
            limiter.ensureAllowed("amina");
        }
        assertDoesNotThrow(() -> limiter.ensureAllowed("amina"));
    }

    @Test
    void studentsAreCountedSeparatelyWhateverTheCapitalLetters() {
        OrderRateLimiter limiter = new OrderRateLimiter(1, 10);
        limiter.recordPlaced("Amina ");

        assertThrows(OrderException.class, () -> limiter.ensureAllowed("amina"));
        assertDoesNotThrow(() -> limiter.ensureAllowed("brian"));
    }

    @Test
    void aOneMinuteWindowIsSaidInTheSingular() {
        OrderRateLimiter limiter = new OrderRateLimiter(1, 1);
        limiter.recordPlaced("amina");

        OrderException e = assertThrows(OrderException.class, () -> limiter.ensureAllowed("amina"));

        assertTrue(e.getMessage().contains("1 minute before"), e.getMessage());
    }
}
