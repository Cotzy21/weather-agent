package no.weatheragent.security;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowLimiterTest {
    private final AtomicLong time = new AtomicLong(1_000);
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(time.get()); }
    };

    @Test void rotatingIdentitiesCannotEvictActiveLimitsOrCreateUnlimitedBudgets() {
        var limiter = new SlidingWindowLimiter(1, 60_000, clock, 2);
        assertEquals(0, limiter.tryAcquire("first"));
        assertEquals(0, limiter.tryAcquire("second"));
        assertEquals(0, limiter.tryAcquire("overflow-first"));
        for (int i = 0; i < 10_000; i++) assertTrue(limiter.tryAcquire("rotating-" + i) > 0);
        assertTrue(limiter.tryAcquire("first") > 0);
        assertTrue(limiter.tryAcquire("second") > 0);
        time.addAndGet(60_001);
        assertEquals(0, limiter.tryAcquire("new-first"));
        assertEquals(0, limiter.tryAcquire("new-second"));
        assertEquals(0, limiter.tryAcquire("new-overflow"));
    }

    @Test void concurrentCallsCannotOverrunASingleBudget() throws Exception {
        var limiter = new SlidingWindowLimiter(10, 60_000, clock, 2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var calls = new java.util.ArrayList<java.util.concurrent.Future<Long>>();
            for (int i = 0; i < 100; i++) calls.add(executor.submit(() -> limiter.tryAcquire("one")));
            int accepted = 0;
            for (var call : calls) if (call.get() == 0) accepted++;
            assertEquals(10, accepted);
        }
    }
}
