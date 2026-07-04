package no.weatheragent.habit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vanene til en bruker: opprett/slett, logg per dag (upsert), og les alt
 * samlet med siste dagers logger + streak. Alt scopes til eieren.
 */
@Service
public class HabitService {

    /** Rimelig tak - en vaneliste på 100 rader er ikke lenger en vane. */
    static final int MAX_HABITS = 20;

    /** Hvor mange dager bakover rutenettet i UI-et viser. */
    public static final int GRID_DAYS = 7;

    private final HabitRepository habits;
    private final HabitLogRepository logs;

    public HabitService(HabitRepository habits, HabitLogRepository logs) {
        this.habits = habits;
        this.logs = logs;
    }

    /** En vane med loggene for rutenettet og gjeldende streak. */
    public record HabitWithLogs(Habit habit, List<HabitLog> recentLogs, int streakDays) {
    }

    @Transactional
    public Habit create(UUID userId, String name, String emoji, String unit) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Vanen må ha et navn.");
        }
        if (habits.findByUserIdOrderByCreatedAtAsc(userId).size() >= MAX_HABITS) {
            throw new IllegalArgumentException(
                    "Du har allerede " + MAX_HABITS + " vaner - slett en gammel først.");
        }
        return habits.save(new Habit(userId, name.trim(), emoji, unit == null ? "" : unit.trim()));
    }

    @Transactional
    public boolean delete(UUID habitId, UUID userId) {
        if (habits.findByIdAndUserId(habitId, userId).isEmpty()) {
            return false;
        }
        logs.deleteByHabitId(habitId);
        return habits.deleteByIdAndUserId(habitId, userId) > 0;
    }

    /**
     * Sett dagens verdi for en vane (overskriver eventuell gammel).
     * Verdi 0 eller lavere betyr «fjern loggen» - slik nullstiller UI-et en celle.
     */
    @Transactional
    public void log(UUID habitId, UUID userId, LocalDate date, double value) {
        habits.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Fant ikke vanen."));

        HabitLog existing = logs.findByHabitIdAndDate(habitId, date).orElse(null);
        if (value <= 0) {
            if (existing != null) {
                logs.delete(existing);
            }
            return;
        }
        if (existing != null) {
            existing.setValue(value);
            logs.save(existing);
        } else {
            logs.save(new HabitLog(habitId, date, value));
        }
    }

    /** Alle vanene med logger for de siste {@link #GRID_DAYS} dagene + streak. */
    @Transactional(readOnly = true)
    public List<HabitWithLogs> listFor(UUID userId, LocalDate today) {
        List<Habit> all = habits.findByUserIdOrderByCreatedAtAsc(userId);
        if (all.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = all.stream().map(Habit::getId).toList();
        Map<UUID, List<HabitLog>> recent = logs
                .findByHabitIdInAndDateGreaterThanEqual(ids, today.minusDays(GRID_DAYS - 1))
                .stream()
                .collect(Collectors.groupingBy(HabitLog::getHabitId));

        return all.stream()
                .map(h -> new HabitWithLogs(h,
                        recent.getOrDefault(h.getId(), List.of()),
                        streak(h.getId(), today)))
                .toList();
    }

    /**
     * Dager PÅ RAD med logg, telt bakover fra i dag (eller i går - dagens
     * logging kan komme senere på kvelden uten at streaken skal se brutt ut).
     */
    private int streak(UUID habitId, LocalDate today) {
        List<LocalDate> dates = logs.findByHabitIdOrderByDateDesc(habitId).stream()
                .map(HabitLog::getDate)
                .toList();
        if (dates.isEmpty()) {
            return 0;
        }

        LocalDate day = dates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (dates.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }
}
