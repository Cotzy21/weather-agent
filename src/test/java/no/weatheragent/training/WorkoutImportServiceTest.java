package no.weatheragent.training;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkoutImportServiceTest {

    private static final String CSV = """
            Aktivitetstype,Dato,Tittel,Distanse,Kalorier,Tid
            Løping,2026-07-01 18:00:00,"Kveldsløp","5,0","400","00:28:15"
            Løping,2026-07-01 18:00:00,"Kveldsløp","5,0","400","00:28:15"
            Styrketrening,2026-06-30 17:00:00,"Push A","0,0","310","00:52:10"
            """;

    private final WorkoutRepository repo = mock(WorkoutRepository.class);
    private final WorkoutImportService service = new WorkoutImportService(repo);
    private final UUID user = UUID.randomUUID();

    @Test
    void importsActivitiesAndSkipsDuplicatesInFile() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());

        WorkoutImportService.ImportResult r = service.importGarmin(user, CSV);

        assertEquals(2, r.imported()); // duplikatraden i fila hoppes over
        assertEquals(1, r.skipped());
    }

    @Test
    void skipsWorkoutsThatAlreadyExist() {
        Workout existing = new Workout(user, LocalDate.of(2026, 6, 30), "Push A", "STYRKE",
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(), null);
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of(existing));

        WorkoutImportService.ImportResult r = service.importGarmin(user, CSV);

        assertEquals(1, r.imported()); // bare løpeturen er ny
        assertEquals(2, r.skipped());
    }

    @Test
    void mapsGarminFieldsIntoWorkoutContent() {
        when(repo.findByUserIdOrderByDateDescCreatedAtDesc(user)).thenReturn(List.of());
        ArgumentCaptor<Workout> saved = ArgumentCaptor.forClass(Workout.class);

        service.importGarmin(user, CSV);

        verify(repo, org.mockito.Mockito.times(2)).save(saved.capture());
        Workout run = saved.getAllValues().getFirst();
        assertEquals("LØPING", run.getType());
        assertEquals("Kveldsløp", run.getTitle());
        assertEquals(5.0, run.getContent().path("distanceKm").asDouble(), 0.001);
        assertEquals(400, run.getContent().path("kcal").asInt());
        assertEquals(28, run.getContent().path("durationMin").asInt());
        assertEquals("Importert fra Garmin", run.getNotes());
    }
}
