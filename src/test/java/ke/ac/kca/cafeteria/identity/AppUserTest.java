package ke.ac.kca.cafeteria.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AppUserTest {

    private final Instant now = Instant.parse("2026-10-05T10:00:00Z");

    private AppUser user() {
        return new AppUser("  24/00123 ", "hash", "Test User");
    }

    @Test
    void usernameIsTrimmedAndLowerCased() {
        assertEquals("24/00123", user().getUsername());
        assertEquals("abc", AppUser.normalize(" ABC "));
    }

    @Test
    void newUserHasPublicIdAndIsActive() {
        AppUser user = user();
        assertNotNull(user.getPublicId());
        assertTrue(user.isActive());
    }

    @Test
    void fiveFailedLoginsLockTheAccountForFifteenMinutes() {
        AppUser user = user();
        for (int i = 0; i < AppUser.MAX_FAILED_ATTEMPTS; i++) {
            assertFalse(user.isLockedAt(now));
            user.registerFailedLogin(now);
        }
        assertTrue(user.isLockedAt(now));
        assertTrue(user.isLockedAt(now.plusSeconds(14 * 60)));
        assertFalse(user.isLockedAt(now.plusSeconds(16 * 60)));
    }

    @Test
    void successfulLoginClearsFailuresAndLock() {
        AppUser user = user();
        user.registerFailedLogin(now);
        user.registerFailedLogin(now);
        user.registerSuccessfulLogin();
        assertEquals(0, user.getFailedAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void failureAfterExpiredLockStartsCountingAgain() {
        AppUser user = user();
        for (int i = 0; i < AppUser.MAX_FAILED_ATTEMPTS; i++) {
            user.registerFailedLogin(now);
        }
        Instant later = now.plusSeconds(20 * 60);
        user.registerFailedLogin(later);
        assertFalse(user.isLockedAt(later));
        assertEquals(1, user.getFailedAttempts());
    }
}
