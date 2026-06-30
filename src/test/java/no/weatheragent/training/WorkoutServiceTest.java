package no.weatheragent.training;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkoutServiceTest {

    private final WorkoutRepository repo = mock(WorkoutRepository.class);
    private final WorkoutService service = new WorkoutService(repo);
    private final UUID user = UUID.randomUUID();

    @Test
    void logBuildsWorkoutWithSets() {
        when(repo.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));

        Workout w = service.log(user, LocalDate.of(2026, 6, 30), "Push", List.of(
                new SetInput("Benkpress", 5, 80),
                new SetInput("Benkpress", 5, 82.5)));

        assertEquals("Push", w.getTitle());
        assertEquals(2, w.getSets().size());
        assertEquals(0, w.getSets().get(0).getPosition());
        assertEquals(1, w.getSets().get(1).getPosition());
        verify(repo).save(any(Workout.class));
    }

    @Test
    void deleteReturnsTrueWhenRemoved() {
        when(repo.deleteByIdAndUserId(any(), eq(user))).thenReturn(1L);
        assertTrue(service.delete(UUID.randomUUID(), user));
    }

    @Test
    void deleteReturnsFalseWhenNothing() {
        when(repo.deleteByIdAndUserId(any(), eq(user))).thenReturn(0L);
        assertFalse(service.delete(UUID.randomUUID(), user));
    }

    @Test
    void listScopesToUser() {
        service.listFor(user);
        verify(repo).findByUserIdWithSets(user);
    }
}
