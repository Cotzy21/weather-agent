package no.weatheragent.recovery;

import no.weatheragent.training.Workout;
import no.weatheragent.training.WorkoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Setter sammen restitusjonsrapporten for en bruker: skadevarsler
 * (volumhopp), hviledag-signal og restitusjonssteg for de siste øktene.
 * All analyse skjer lokalt på treningsloggen - ingen eksterne kall.
 */
@Service
public class RecoveryService {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final WorkoutRepository workouts;

    public RecoveryService(WorkoutRepository workouts) {
        this.workouts = workouts;
    }

    public record RecoveryReport(List<RecoveryAdvice> advice,
                                 List<InjuryWarning> warnings,
                                 int streakDays,
                                 boolean restDaySuggested) {
    }

    @Transactional(readOnly = true)
    public RecoveryReport report(UUID userId) {
        List<Workout> all = workouts.findByUserIdOrderByDateDescCreatedAtDesc(userId);
        LocalDate today = LocalDate.now(OSLO);

        int streak = RecoveryAdvisor.trainingStreak(all, today);
        return new RecoveryReport(
                RecoveryAdvisor.advise(all, today),
                InjuryRiskAnalyzer.analyze(all, today),
                streak,
                streak >= RecoveryAdvisor.REST_DAY_STREAK);
    }
}
