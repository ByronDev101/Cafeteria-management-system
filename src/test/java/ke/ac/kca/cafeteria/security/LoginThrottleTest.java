package ke.ac.kca.cafeteria.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

class LoginThrottleTest {

    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        throttle = new LoginThrottle(3, Duration.ofMinutes(15));
    }

    private static Authentication attemptFrom(String address, String username) {
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated(username, "x");
        if (address != null) {
            token.setDetails(new WebAuthenticationDetails(address, "session-1"));
        }
        return token;
    }

    private void fail(String address, String username) {
        throttle.onFailure(new AuthenticationFailureBadCredentialsEvent(attemptFrom(address, username),
                new BadCredentialsException("bad")));
    }

    @Test
    void anAddressIsBlockedAfterTooManyFailures() {
        fail("10.0.0.5", "amina");
        fail("10.0.0.5", "amina");
        assertFalse(throttle.isBlocked("10.0.0.5"));
        fail("10.0.0.5", "amina");

        assertTrue(throttle.isBlocked("10.0.0.5"));
        assertTrue(throttle.retryAfter("10.0.0.5").toSeconds() > 0);
    }

    @Test
    void guessingDifferentAccountsFromOneAddressStillCounts() {
        fail("10.0.0.5", "amina");
        fail("10.0.0.5", "brian");
        fail("10.0.0.5", "carol");

        assertTrue(throttle.isBlocked("10.0.0.5"));
    }

    @Test
    void otherAddressesAreNotAffected() {
        for (int i = 0; i < 5; i++) {
            fail("10.0.0.5", "amina");
        }
        assertTrue(throttle.isBlocked("10.0.0.5"));
        assertFalse(throttle.isBlocked("10.0.0.6"));
    }

    @Test
    void aLockedAccountAttemptCountsToo() {
        for (int i = 0; i < 3; i++) {
            throttle.onFailure(new AuthenticationFailureLockedEvent(attemptFrom("10.0.0.5", "amina"),
                    new LockedException("locked")));
        }
        assertTrue(throttle.isBlocked("10.0.0.5"));
    }

    @Test
    void aSuccessfulSignInCanNeverWipeTheCountBecauseTheThrottleDoesNotListenForSuccesses() {
        fail("10.0.0.5", "amina");
        fail("10.0.0.5", "amina");
        fail("10.0.0.5", "amina");
        assertTrue(throttle.isBlocked("10.0.0.5"));

        for (Method method : LoginThrottle.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(AuthenticationSuccessEvent.class.isAssignableFrom(parameter),
                        "the throttle must not react to successes: " + method.getName());
            }
        }
    }

    @Test
    void attemptsWithNoAddressAreCountedTogetherUnderUnknown() {
        for (int i = 0; i < 3; i++) {
            fail(null, "amina");
        }
        assertTrue(throttle.isBlocked("unknown"));
        assertTrue(throttle.isBlocked(null));
        assertTrue(throttle.isBlocked("  "));
        assertFalse(throttle.isBlocked("10.0.0.5"));
    }
}
