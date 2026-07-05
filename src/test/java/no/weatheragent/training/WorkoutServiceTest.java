package no.weatheragent.training;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID user = UUID.randomUUID();

    @Test
    void logDelegatesToRepository() {
        when(repo.save(any(Workout.class))).thenAnswer(inv -> inv.getArgument(0));
        Workout w = service.log(user, LocalDate.of(2026, 6, 30), "Push", "STYRKE", mapper.createObjectNode(), null);
        assertEquals("STYRKE", w.getType());
        verify(repo).save(any(Workout.class));
    }

    @Test
    void listSinceDelegatesToDateScopedQuery() {
        LocalDate since = LocalDate.of(2026, 6, 29);
        when(repo.findByUserIdAndDateGreaterThanEqualOrderByDateDescCreatedAtDesc(user, since))
                .thenReturn(List.of());
        service.listSince(user, since);
        verify(repo).findByUserIdAndDateGreaterThanEqualOrderByDateDescCreatedAtDesc(user, since);
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
    void progressionReadsBestWeightAndVolumePerDay() throws Exception {
        String day1 = """
                {"blocks":[{"kind":"exercise","name":"Benkpress",
                  "sets":[{"reps":5,"weightKg":80},{"reps":5,"weightKg":82.5}]}]}""";
        String day2 = """
                {"blocks":[{"kind":"superset","exercises":[
                  {"name":"Benkpress","sets":[{"reps":3,"weightKg":90}]},
                  {"name":"Roing","sets":[{"reps":8,"weightKg":60}]}]}]}""";
        Workout w1 = new Workout(user, LocalDate.of(2026, 6, 1), "A", "STYRKE", mapper.readTree(day1), null);
        Workout w2 = new Workout(user, LocalDate.of(2026, 6, 8), "B", "STYRKE", mapper.readTree(day2), null);
        when(repo.findByUserIdAndTypeOrderByDateAsc(user, "STYRKE")).thenReturn(List.of(w1, w2));

        List<ProgressPoint> prog = service.progression(user, "benkpress");

        assertEquals(2, prog.size());
        assertEquals(82.5, prog.get(0).maxWeight());
        assertEquals(812.5, prog.get(0).volume(), 0.001); // 5*80 + 5*82.5
        assertEquals(90.0, prog.get(1).maxWeight());
    }

    @Test
    void progressionIncludesDropsetWeights() throws Exception {
        String day = """
                {"blocks":[{"kind":"dropset","name":"Curl",
                  "drops":[{"reps":10,"weightKg":15},{"reps":8,"weightKg":12}]}]}""";
        Workout w = new Workout(user, LocalDate.of(2026, 6, 1), "A", "STYRKE", mapper.readTree(day), null);
        when(repo.findByUserIdAndTypeOrderByDateAsc(user, "STYRKE")).thenReturn(List.of(w));

        List<ProgressPoint> prog = service.progression(user, "Curl");

        assertEquals(1, prog.size());
        assertEquals(15.0, prog.get(0).maxWeight());
    }
}
