package ke.ac.kca.cafeteria.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    /** A clock the test can move forward. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-28T09:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MovableClock clock;
    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        limiter = new RateLimiter(3, Duration.ofMinutes(10), clock);
    }

    @Test
    void aKeyIsAllowedUpToTheLimitAndThenBlocked() {
        for (int i = 0; i < 3; i++) {
            assertFalse(limiter.isLimited("a"), "event " + i);
            limiter.record("a");
        }
        assertTrue(limiter.isLimited("a"));
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.record("a");
        }
        assertTrue(limiter.isLimited("a"));
        assertFalse(limiter.isLimited("b"));
    }

    @Test
    void eventsExpireAsTheWindowSlidesPastThem() {
        limiter.record("a");
        clock.advance(Duration.ofMinutes(4));
        limiter.record("a");
        clock.advance(Duration.ofMinutes(4));
        limiter.record("a");
        assertTrue(limiter.isLimited("a"));

        clock.advance(Duration.ofMinutes(3));        // the first event is now 11 minutes old
        assertFalse(limiter.isLimited("a"));
        limiter.record("a");
        assertTrue(limiter.isLimited("a"));           // 2 older events plus the new one
    }

    @Test
    void anEventExactlyOneWindowOldNoLongerCounts() {
        for (int i = 0; i < 3; i++) {
            limiter.record("a");
        }
        clock.advance(Duration.ofMinutes(10));
        assertFalse(limiter.isLimited("a"));
    }

    @Test
    void retryAfterSaysHowLongUntilTheOldestEventExpires() {
        assertEquals(Duration.ZERO, limiter.retryAfter("a"));
        limiter.record("a");
        clock.advance(Duration.ofMinutes(2));
        limiter.record("a");
        limiter.record("a");

        assertEquals(Duration.ofMinutes(8), limiter.retryAfter("a"));
        clock.advance(Duration.ofMinutes(8));
        assertEquals(Duration.ZERO, limiter.retryAfter("a"));
        assertFalse(limiter.isLimited("a"));
    }

    @Test
    void tryAcquireRecordsOnlyWhileThereIsRoom() {
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));
        assertFalse(limiter.tryAcquire("a"));

        clock.advance(Duration.ofMinutes(11));
        assertTrue(limiter.tryAcquire("a"));
    }

    @Test
    void clearForgetsAKey() {
        for (int i = 0; i < 3; i++) {
            limiter.record("a");
        }
        limiter.clear("a");
        assertFalse(limiter.isLimited("a"));
        assertEquals(0, limiter.trackedKeys());
    }

    @Test
    void recordingFarMoreThanTheLimitStillExpiresOnTime() {
        for (int i = 0; i < 50; i++) {
            limiter.record("a");
            clock.advance(Duration.ofSeconds(1));
        }
        assertTrue(limiter.isLimited("a"));
        clock.advance(Duration.ofMinutes(10));
        assertFalse(limiter.isLimited("a"));
    }

    @Test
    void expiredKeysAreForgottenSoMemoryDoesNotGrow() {
        for (int i = 0; i < 20; i++) {
            limiter.record("client-" + i);
        }
        assertEquals(20, limiter.trackedKeys());
        clock.advance(Duration.ofMinutes(11));
        for (int i = 0; i < 20; i++) {
            assertFalse(limiter.isLimited("client-" + i));
        }
        assertEquals(0, limiter.trackedKeys());
    }

    @Test
    void aLimitMustMakeSense() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, Duration.ofMinutes(-1)));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(1, null));
    }
}
