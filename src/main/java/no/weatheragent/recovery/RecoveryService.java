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
        return report(userId, false);
    }

    /** {@code english=true} gir råd/varsler på engelsk (ellers norsk). */
    @Transactional(readOnly = true)
    public RecoveryReport report(UUID userId, boolean english) {
        LocalDate today = LocalDate.now(OSLO);
        List<Workout> recent = workouts.findByUserIdAndDateBetweenOrderByDateDescCreatedAtDesc(
                userId, InjuryRiskAnalyzer.earliestRequiredDate(today), today);

        int streak = RecoveryAdvisor.trainingStreakFromDates(workouts.findTrainingDates(userId, today), today);
        return new RecoveryReport(
                RecoveryAdvisor.advise(recent, today, english),
                InjuryRiskAnalyzer.analyze(recent, today, english),
                streak,
                streak >= RecoveryAdvisor.REST_DAY_STREAK);
    }
}
