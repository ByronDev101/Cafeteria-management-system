package ke.ac.kca.cafeteria.security;

import java.time.Duration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Slows down password guessing from one place. The account lock already stops repeated guesses at one account; this
 * counts every failed sign-in by the address it came from, so trying many accounts from one address is stopped too.
 * A success never resets the count, so a person with one valid account cannot use it to keep guessing others.
 */
public class LoginThrottle {

    private static final String UNKNOWN = "unknown";

    private final RateLimiter failures;

    public LoginThrottle(int maxFailures, Duration window) {
        this(new RateLimiter(maxFailures, window));
    }

    public LoginThrottle(RateLimiter failures) {
        this.failures = failures;
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        failures.record(clientOf(event.getAuthentication()));
    }

    public boolean isBlocked(String clientAddress) {
        return failures.isLimited(key(clientAddress));
    }

    public Duration retryAfter(String clientAddress) {
        return failures.retryAfter(key(clientAddress));
    }

    static String clientOf(Authentication authentication) {
        Object details = authentication == null ? null : authentication.getDetails();
        if (details instanceof WebAuthenticationDetails web && web.getRemoteAddress() != null) {
            return key(web.getRemoteAddress());
        }
        return UNKNOWN;
    }

    private static String key(String address) {
        return address == null || address.isBlank() ? UNKNOWN : address.trim();
    }
}
