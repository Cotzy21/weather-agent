package no.weatheragent.training;

import no.weatheragent.habit.HabitService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Knytter søvnloggen i habit trackeren til treningen: dagsform for Trening-
 * fanen, og en kort søvnlinje til AI-assistentens kontekst.
 */
@Service
public class ReadinessService {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final int CONTEXT_NIGHTS = 7;

    private final HabitService habits;

    public ReadinessService(HabitService habits) {
        this.habits = habits;
    }

    public Optional<Readiness> today(UUID userId, boolean english) {
        LocalDate today = LocalDate.now(OSLO);
        return ReadinessAdvisor.assess(habits.sleepHours(userId, today.minusDays(3)), today, english);
    }

    /**
     * Linje til AI-prompten, f.eks. «Søvn siste netter: 2026-09-27 7,5 t, …
     * Dagsform: LAV». Tom når brukeren ikke logger søvn.
     */
    public String promptContext(UUID userId) {
        LocalDate today = LocalDate.now(OSLO);
        Map<LocalDate, Double> sleep = habits.sleepHours(userId, today.minusDays(CONTEXT_NIGHTS - 1));
        if (sleep.isEmpty()) {
            return "";
        }
        String nights = sleep.entrySet().stream()
                .map(e -> e.getKey() + " " + String.format(Locale.ROOT, "%.1f", e.getValue()) + " t")
                .collect(Collectors.joining(", "));
        String level = ReadinessAdvisor.assess(sleep, today, false)
                .map(r -> "\nDagsform i dag (fra søvn): " + r.level()
                        + " - tilpass intensitet/volum i økta for i dag etter dette.")
                .orElse("");
        return "\nSøvn siste netter: " + nights + level;
    }
}
