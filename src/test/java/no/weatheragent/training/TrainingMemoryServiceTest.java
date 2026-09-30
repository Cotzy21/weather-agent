package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrainingMemoryServiceTest {

    private static final UUID USER = UUID.randomUUID();
    /** En tirsdag. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    private final TrainingMemoryRepository memories = mock(TrainingMemoryRepository.class);
    private final WorkoutRepository workouts = mock(WorkoutRepository.class);
    private final TrainingMemoryService service = new TrainingMemoryService(memories, workouts);
    private final ObjectMapper mapper = new ObjectMapper();

    private Workout strength(LocalDate date, String... exercises) {
        StringBuilder blocks = new StringBuilder();
        for (String e : exercises) {
            if (blocks.length() > 0) blocks.append(',');
            blocks.append("{\"kind\":\"exercise\",\"name\":\"").append(e).append("\",\"sets\":[{\"reps\":8,\"weightKg\":50}]}");
        }
        try {
            JsonNode content = mapper.readTree("{\"blocks\":[" + blocks + "]}");
            return new Workout(USER, date, "Økt", "STYRKE", content, null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void savesCleanedListsWithoutDuplicates() {
        service.save(USER, List.of(" Benkpress ", "benkpress", "Knebøy, tung"), List.of("Burpees"), "  Vondt kne  ");

        ArgumentCaptor<TrainingMemory> saved = ArgumentCaptor.forClass(TrainingMemory.class);
        verify(memories).save(saved.capture());
        assertEquals(List.of("Benkpress", "Knebøy tung"), saved.getValue().liked());
        assertEquals(List.of("Burpees"), saved.getValue().disliked());
        assertEquals("Vondt kne", saved.getValue().notes());
    }

    @Test
    void refusesExerciseThatIsBothLikedAndDisliked() {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(USER, List.of("Burpees"), List.of("burpees"), ""));
    }

    @Test
    void refusesTooLongNotes() {
        assertThrows(IllegalArgumentException.class,
                () -> service.save(USER, List.of(), List.of(), "x".repeat(TrainingMemoryService.MAX_NOTES_LENGTH + 1)));
    }

    @Test
    void computesHabitsFromWorkouts() {
        List<Workout> recent = new ArrayList<>();
        // Mandag og torsdag hver uke de siste 4 ukene -> 8 økter.
        for (int week = 0; week < 4; week++) {
            recent.add(strength(TODAY.minusDays(1 + 7L * week), "Benkpress", "Knebøy"));
            recent.add(strength(TODAY.minusDays(5 + 7L * week), "Markløft", "Knebøy"));
        }
        when(memories.findById(USER)).thenReturn(Optional.empty());
        when(workouts.findByUserIdAndDateGreaterThanEqualOrderByDateDescCreatedAtDesc(eq(USER), any()))
                .thenReturn(recent);

        TrainingMemoryProfile p = service.profile(USER, TODAY);

        assertEquals(8, p.workoutsLast28Days());
        assertEquals(2.0, p.avgPerWeek());
        assertEquals(List.of(1, 4), p.usualDays());
        assertEquals("Knebøy", p.topExercises().get(0));
        assertEquals(TODAY.minusDays(1), p.lastWorkout());
    }

    @Test
    void promptContextIsEmptyForNewUser() {
        when(memories.findById(USER)).thenReturn(Optional.empty());
        when(workouts.findByUserIdAndDateGreaterThanEqualOrderByDateDescCreatedAtDesc(eq(USER), any()))
                .thenReturn(List.of());

        assertEquals("", service.promptContext(USER, TODAY));
    }

    @Test
    void promptContextListsDislikesAndHabits() {
        when(memories.findById(USER)).thenReturn(Optional.of(
                new TrainingMemory(USER, List.of("Benkpress"), List.of("Burpees"), "Har bare manualer hjemme")));
        when(workouts.findByUserIdAndDateGreaterThanEqualOrderByDateDescCreatedAtDesc(eq(USER), any()))
                .thenReturn(List.of(strength(TODAY.minusDays(1), "Benkpress"), strength(TODAY.minusDays(8), "Benkpress")));

        String ctx = service.promptContext(USER, TODAY);

        assertTrue(ctx.contains("Liker: Benkpress"));
        assertTrue(ctx.contains("Liker IKKE (ikke foreslå): Burpees"));
        assertTrue(ctx.contains("Har bare manualer hjemme"));
        assertTrue(ctx.contains("oftest mandag"));
    }
}
