package no.weatheragent.training;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final Clock clock = new Clock() {
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    };
    private final UUID user = UUID.randomUUID();

    @Test
    void blocksAfterTheHourlyLimitAndTellsWhenToRetry() {
        AiRateLimiter limiter = new AiRateLimiter(2, 100, clock);
        limiter.check(user);
        now.addAndGet(60_000);
        limiter.check(user);

        var e = assertThrows(AiRateLimiter.LimitExceededException.class, () -> limiter.check(user));
        assertEquals(3540, e.retryAfterSeconds()); // eldste kall er 60 s gammelt
    }

    @Test
    void allowsCallsAgainWhenTheWindowHasPassed() {
        AiRateLimiter limiter = new AiRateLimiter(1, 100, clock);
        limiter.check(user);
        now.addAndGet(3_600_001);
        assertDoesNotThrow(() -> limiter.check(user));
    }

    @Test
    void dailyLimitAppliesAcrossHours() {
        AiRateLimiter limiter = new AiRateLimiter(10, 3, clock);
        for (int i = 0; i < 3; i++) {
            limiter.check(user);
            now.addAndGet(3_700_000); // ny time hver gang
        }
        assertThrows(AiRateLimiter.LimitExceededException.class, () -> limiter.check(user));
    }

    @Test
    void usersHaveSeparateBudgets() {
        AiRateLimiter limiter = new AiRateLimiter(1, 100, clock);
        limiter.check(user);
        assertDoesNotThrow(() -> limiter.check(UUID.randomUUID()));
    }

    @Test
    void globalDailyCapStopsEveryoneEvenWhenEachUserIsWithinTheirOwnQuota() {
        AiRateLimiter limiter = new AiRateLimiter(10, 10, 3, clock);
        limiter.check(UUID.randomUUID());
        limiter.check(UUID.randomUUID());
        limiter.check(UUID.randomUUID());

        var e = assertThrows(AiRateLimiter.LimitExceededException.class, () -> limiter.check(UUID.randomUUID()));
        assertTrue(e.getMessage().contains("alle brukere"));

        now.addAndGet(86_400_001); // et døgn senere er det åpnet igjen
        assertDoesNotThrow(() -> limiter.check(UUID.randomUUID()));
    }

    @Test
    void aBlockedCallDoesNotUseUpTheGlobalBudget() {
        AiRateLimiter limiter = new AiRateLimiter(1, 100, 2, clock);
        UUID a = UUID.randomUUID();
        limiter.check(a);
        assertThrows(AiRateLimiter.LimitExceededException.class, () -> limiter.check(a)); // over egen timegrense
        assertDoesNotThrow(() -> limiter.check(UUID.randomUUID())); // det globale budsjettet har fortsatt plass
    }
}
