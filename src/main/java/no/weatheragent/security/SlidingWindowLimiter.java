package no.weatheragent.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glidende vindu-teller per nøkkel (IP, bruker ...): maks {@code limit} treff per {@code windowMs}.
 * Ligger i minnet (per instans). {@link #tryAcquire} teller bare når kallet slippes gjennom.
 */
public class SlidingWindowLimiter {

    private final int limit;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private long lastPurge;

    public SlidingWindowLimiter(int limit, long windowMs, Clock clock) {
        this.limit = limit;
        this.windowMs = windowMs;
        this.clock = clock;
        this.lastPurge = clock.millis();
    }

    /** @return 0 hvis kallet er tillatt (og talt), ellers sekunder til neste ledige plass. */
    public long tryAcquire(String key) {
        long now = clock.millis();
        purgeIfDue(now);
        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
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
    }

    /** Rydder bort nøkler uten nylige treff, så minnet ikke vokser med hver ny IP. */
    private void purgeIfDue(long now) {
        if (now - lastPurge < windowMs || hits.size() < 1000) {
            return;
        }
        lastPurge = now;
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last >= windowMs;
            }
        });
    }
}
