package no.weatheragent.habit;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HabitServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 4);

    private final HabitRepository habits = mock(HabitRepository.class);
    private final HabitLogRepository logs = mock(HabitLogRepository.class);
    private final HabitService service = new HabitService(habits, logs);

    private static Habit kaffe() {
        return new Habit(USER, "Koffein", "☕", "kopper");
    }

    @Test
    void createsHabitForUser() {
        when(habits.findByUserIdOrderByCreatedAtAsc(USER)).thenReturn(List.of());
        when(habits.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Habit habit = service.create(USER, "  Lesing ", "📖", null);

        assertEquals("Lesing", habit.getName());
        assertTrue(habit.isBoolean()); // ingen enhet -> ja/nei-vane
    }

    @Test
    void refusesWhenHabitLimitReached() {
        when(habits.findByUserIdOrderByCreatedAtAsc(USER))
                .thenReturn(Collections.nCopies(HabitService.MAX_HABITS, kaffe()));

        assertThrows(IllegalArgumentException.class,
                () -> service.create(USER, "En til", "", ""));
    }

    @Test
    void loggingUpsertsExistingDay() {
        UUID habitId = UUID.randomUUID();
        when(habits.findByIdAndUserId(habitId, USER)).thenReturn(Optional.of(kaffe()));
        HabitLog existing = new HabitLog(habitId, TODAY, 2);
        when(logs.findByHabitIdAndDate(habitId, TODAY)).thenReturn(Optional.of(existing));

        service.log(habitId, USER, TODAY, 4);

        assertEquals(4, existing.getValue());
        verify(logs).save(existing);
    }

    @Test
    void zeroValueDeletesTheDaysLog() {
        UUID habitId = UUID.randomUUID();
        when(habits.findByIdAndUserId(habitId, USER)).thenReturn(Optional.of(kaffe()));
        HabitLog existing = new HabitLog(habitId, TODAY, 2);
        when(logs.findByHabitIdAndDate(habitId, TODAY)).thenReturn(Optional.of(existing));

        service.log(habitId, USER, TODAY, 0);

        verify(logs).delete(existing);
        verify(logs, never()).save(any());
    }

    @Test
    void loggingSomeoneElsesHabitIsRejected() {
        UUID habitId = UUID.randomUUID();
        when(habits.findByIdAndUserId(habitId, USER)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.log(habitId, USER, TODAY, 1));
    }

    @Test
    void streakCountsConsecutiveDaysAndSurvivesMissingToday() {
        UUID habitId = UUID.randomUUID();
        Habit habit = kaffe();
        when(habits.findByUserIdOrderByCreatedAtAsc(USER)).thenReturn(List.of(habit));
        when(logs.findByHabitIdInAndDateGreaterThanEqual(any(), any())).thenReturn(List.of());
        // Ingen logg i dag, men i går + to dager før -> streak 3.
        when(logs.findByHabitIdOrderByDateDesc(any())).thenReturn(List.of(
                new HabitLog(habitId, TODAY.minusDays(1), 1),
                new HabitLog(habitId, TODAY.minusDays(2), 1),
                new HabitLog(habitId, TODAY.minusDays(3), 1),
                new HabitLog(habitId, TODAY.minusDays(5), 1))); // hull -> teller ikke

        List<HabitService.HabitWithLogs> list = service.listFor(USER, TODAY);

        assertEquals(3, list.getFirst().streakDays());
    }
}
