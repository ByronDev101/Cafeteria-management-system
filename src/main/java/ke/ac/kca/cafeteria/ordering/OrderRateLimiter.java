package ke.ac.kca.cafeteria.ordering;

import ke.ac.kca.cafeteria.security.RateLimiter;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stops one student placing a flood of orders: by default at most 5 orders in 10 minutes. Only orders that were
 * actually placed count, and a repeated submit that returns the earlier order does not.
 */
@Component
public class OrderRateLimiter {

    private final RateLimiter limiter;

    public OrderRateLimiter(@Value("${app.rate-limit.orders.max:5}") int max,
                            @Value("${app.rate-limit.orders.window-minutes:10}") long windowMinutes) {
        this.limiter = new RateLimiter(max, Duration.ofMinutes(windowMinutes));
    }

    /** Refuses, with a plain message, when the student has placed too many orders lately. */
    public void ensureAllowed(String username) {
        String key = key(username);
        if (limiter.isLimited(key)) {
            long minutes = Math.max(1, (limiter.retryAfter(key).toSeconds() + 59) / 60);
            throw new OrderException("You have placed several orders in a short time. Please wait about "
                    + minutes + (minutes == 1 ? " minute" : " minutes")
                    + " before placing another, or ask staff for help.");
        }
    }

    public void recordPlaced(String username) {
        limiter.record(key(username));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
