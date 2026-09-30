package no.weatheragent.training;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tak på AI-kall per bruker (glidende vindu per time og per døgn), så én bruker ikke kan brenne
 * gjennom LLM-budsjettet. Ligger i minnet (per instans): nok til å stoppe misbruk, og nullstilles
 * ved restart. Grensene settes med {@code ai.limit.per-hour} og {@code ai.limit.per-day}.
 */
@Component
public class AiRateLimiter {

    /** Kastes når grensen er nådd; {@code retryAfterSeconds} sier når neste kall er lov. */
    public static class LimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;

        public LimitExceededException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private static final long HOUR_MS = 3_600_000L;
    private static final long DAY_MS = 86_400_000L;

    private final int perHour;
    private final int perDay;
    private final int globalPerDay;
    private final Clock clock;
    private final Deque<Long> globalCalls = new ArrayDeque<>();
    private final Map<UUID, Deque<Long>> calls = new ConcurrentHashMap<>();

    @Autowired
    public AiRateLimiter(@Value("${ai.limit.per-hour:20}") int perHour, @Value("${ai.limit.per-day:60}") int perDay,
                         @Value("${ai.limit.global-per-day:1500}") int globalPerDay) {
        this(perHour, perDay, globalPerDay, Clock.systemUTC());
    }

    AiRateLimiter(int perHour, int perDay, Clock clock) {
        this(perHour, perDay, Integer.MAX_VALUE, clock);
    }

    AiRateLimiter(int perHour, int perDay, int globalPerDay, Clock clock) {
        this.perHour = perHour;
        this.perDay = perDay;
        this.globalPerDay = globalPerDay;
        this.clock = clock;
    }

    /** Teller ett kall for brukeren, eller kaster {@link LimitExceededException} uten å telle. */
    public void check(UUID userId) {
        long now = clock.millis();
        Deque<Long> times = calls.computeIfAbsent(userId, u -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= DAY_MS) {
                times.pollFirst();
            }
            long inHour = times.stream().filter(t -> now - t < HOUR_MS).count();
            if (inHour >= perHour) {
                long oldestInHour = times.stream().filter(t -> now - t < HOUR_MS).findFirst().orElse(now);
                throw exceeded("timen", HOUR_MS - (now - oldestInHour));
            }
            if (times.size() >= perDay) {
                throw exceeded("døgnet", DAY_MS - (now - times.peekFirst()));
            }
            // Kostnadsbrems for hele tjenesten: uten den kan noen registrere mange kontoer, som hver får sin kvote.
            synchronized (globalCalls) {
                while (!globalCalls.isEmpty() && now - globalCalls.peekFirst() >= DAY_MS) {
                    globalCalls.pollFirst();
                }
                if (globalCalls.size() >= globalPerDay) {
                    throw new LimitExceededException(
                            "AI-assistenten har nådd dagens grense for alle brukere. Prøv igjen senere i dag.",
                            Math.max(1, (DAY_MS - (now - globalCalls.peekFirst()) + 999) / 1000));
                }
                globalCalls.addLast(now);
            }
            times.addLast(now);
        }
        if (calls.size() > 10_000) {
            calls.entrySet().removeIf(e -> e.getValue().isEmpty());
        }
    }

    private static LimitExceededException exceeded(String period, long waitMs) {
        long seconds = Math.max(1, (waitMs + 999) / 1000);
        long minutes = (seconds + 59) / 60;
        return new LimitExceededException("Du har brukt opp AI-kallene dine for " + period
                + ". Prøv igjen om " + minutes + " min.", seconds);
    }
}
