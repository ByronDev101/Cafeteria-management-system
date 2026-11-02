package ke.ac.kca.cafeteria.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A sliding-window limit: at most {@code maxEvents} events for one key (a client address or a student) inside the
 * window. It keeps only the newest {@code maxEvents} times per key, so memory stays small, and it forgets keys whose
 * events have all expired. It lives in memory, so counts restart when the application restarts.
 */
public final class RateLimiter {

    private static final int PRUNE_ABOVE = 10_000;

    private final int maxEvents;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> events = new HashMap<>();

    public RateLimiter(int maxEvents, Duration window, Clock clock) {
        if (maxEvents < 1) {
            throw new IllegalArgumentException("A rate limit needs at least one event");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("A rate limit needs a window above zero");
        }
        this.maxEvents = maxEvents;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    public RateLimiter(int maxEvents, Duration window) {
        this(maxEvents, window, Clock.systemUTC());
    }

    /** True when the key has already used up its events inside the window. */
    public synchronized boolean isLimited(String key) {
        Deque<Long> times = live(key, clock.millis());
        return times != null && times.size() >= maxEvents;
    }

    /** Notes that one event happened now. */
    public synchronized void record(String key) {
        long now = clock.millis();
        Deque<Long> times = live(key, now);
        if (times == null) {
            times = new ArrayDeque<>();
            events.put(key, times);
        }
        times.addLast(now);
        while (times.size() > maxEvents) {
            times.pollFirst();
        }
        if (events.size() > PRUNE_ABOVE) {
            prune(now);
        }
    }

    /** Records an event unless the key is already limited. Returns false, recording nothing, when it is limited. */
    public synchronized boolean tryAcquire(String key) {
        if (isLimited(key)) {
            return false;
        }
        record(key);
        return true;
    }

    /** How long until the key may act again. Zero when it is not limited. */
    public synchronized Duration retryAfter(String key) {
        long now = clock.millis();
        Deque<Long> times = live(key, now);
        if (times == null || times.size() < maxEvents) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(Math.max(0L, times.peekFirst() + windowMillis - now));
    }

    public synchronized void clear(String key) {
        events.remove(key);
    }

    /** How many keys are being remembered. For tests and monitoring. */
    public synchronized int trackedKeys() {
        return events.size();
    }

    /** The key's events still inside the window, or null when it has none. Expired events are dropped. */
    private Deque<Long> live(String key, long now) {
        Deque<Long> times = events.get(key);
        if (times == null) {
            return null;
        }
        long oldestAllowed = now - windowMillis;
        while (!times.isEmpty() && times.peekFirst() <= oldestAllowed) {
            times.pollFirst();
        }
        if (times.isEmpty()) {
            events.remove(key);
            return null;
        }
        return times;
    }

    private void prune(long now) {
        long oldestAllowed = now - windowMillis;
        Iterator<Map.Entry<String, Deque<Long>>> it = events.entrySet().iterator();
        while (it.hasNext()) {
            Deque<Long> times = it.next().getValue();
            if (times.isEmpty() || times.peekLast() <= oldestAllowed) {
                it.remove();
            }
        }
    }
}
