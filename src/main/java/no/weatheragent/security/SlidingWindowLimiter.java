package no.weatheragent.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Glidende vindu-teller per nøkkel (IP, bruker ...): maks {@code limit} treff per {@code windowMs}.
 * Ligger i minnet (per instans). {@link #tryAcquire} teller bare når kallet slippes gjennom.
 */
public class SlidingWindowLimiter {

    private final int limit;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new HashMap<>();
    private final Deque<Long> overflow = new ArrayDeque<>();
    private final int maxKeys;
    private long lastPurge;

    public SlidingWindowLimiter(int limit, long windowMs, Clock clock) {
        this(limit, windowMs, clock, 4096);
    }

    SlidingWindowLimiter(int limit, long windowMs, Clock clock, int maxKeys) {
        this.limit = limit;
        this.windowMs = windowMs;
        this.clock = clock;
        this.maxKeys = maxKeys;
        this.lastPurge = clock.millis();
    }

    /** @return 0 hvis kallet er tillatt (og talt), ellers sekunder til neste ledige plass. */
    public synchronized long tryAcquire(String key) {
        long now = clock.millis();
        purgeIfDue(now);
        Deque<Long> times = hits.get(key);
        if (times == null) {
            // Never evict an active limit: new identities share a conservative overflow budget.
            // This bounds memory even when an attacker rotates spoofed forwarding headers.
            if (hits.size() >= maxKeys) times = overflow;
            else {
                times = new ArrayDeque<>();
                hits.put(key, times);
            }
        }
        while (!times.isEmpty() && now - times.peekFirst() >= windowMs) {
            times.pollFirst();
        }
        if (times.size() >= limit) {
            long waitMs = windowMs - (now - times.peekFirst());
            return Math.max(1, (waitMs + 999) / 1000);
        }
        times.addLast(now);
        return 0;
    }

    /** Rydder bort nøkler uten nylige treff, så minnet ikke vokser med hver ny IP. */
    private void purgeIfDue(long now) {
        if (now - lastPurge < windowMs) {
            return;
        }
        lastPurge = now;
        hits.values().removeIf(times -> times.isEmpty() || now - times.peekLast() >= windowMs);
    }
}
