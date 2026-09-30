package no.weatheragent.training;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    private static WorkoutRepository.WorkoutKey key(LocalDate date, String title, String type) {
        return new WorkoutRepository.WorkoutKey() {
            public LocalDate getDate() { return date; }
            public String getTitle() { return title; }
            public String getType() { return type; }
        };
    }

    @Test
    void importsActivitiesAndSkipsDuplicatesInFile() {
        when(repo.findKeysBetween(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        WorkoutImportService.ImportResult r = service.importGarmin(user, CSV);

        assertEquals(2, r.imported()); // duplikatraden i fila hoppes over
        assertEquals(1, r.skipped());
    }

    @Test
    void skipsWorkoutsThatAlreadyExist() {
        WorkoutRepository.WorkoutKey existing = key(LocalDate.of(2026, 6, 30), "Push A", "STYRKE");
        when(repo.findKeysBetween(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(existing));

        WorkoutImportService.ImportResult r = service.importGarmin(user, CSV);

        assertEquals(1, r.imported()); // bare løpeturen er ny
        assertEquals(2, r.skipped());
    }

    @Test
    void mapsGarminFieldsIntoWorkoutContent() {
        when(repo.findKeysBetween(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
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

    // --- FIT-import (øvelser og vekter) ---

    private static FitSession fit(UUID clientId, LocalDate date, Double durationMin, String... exercises) {
        List<FitSession.Block> blocks = new java.util.ArrayList<>();
        for (String e : exercises) {
            blocks.add(new FitSession.Block(e, List.of(new FitSession.SetEntry(8, 60), new FitSession.SetEntry(8, 62.5))));
        }
        return new FitSession(clientId, date, null, durationMin, 310.0, 118, 151, blocks);
    }

    private Workout csvStrength(LocalDate date, String title, int durationMin) {
        var content = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        content.put("durationMin", durationMin);
        content.put("kcal", 300);
        content.put("totalSets", 18);
        return new Workout(user, date, title, "STYRKE", content, "Importert fra Garmin");
    }

    @Test
    void fitSessionFillsInExercisesOnTheCsvImportedWorkoutInsteadOfDuplicatingIt() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        Workout fromCsv = csvStrength(day, "Push", 52);
        when(repo.findByUserIdAndClientId(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        when(repo.findByUserIdAndDate(user, day)).thenReturn(List.of(fromCsv));
        UUID clientId = UUID.randomUUID();

        var r = service.importFit(user, List.of(fit(clientId, day, 51.0, "Barbell Bench Press")));

        assertEquals(0, r.imported());
        assertEquals(1, r.merged());
        assertEquals("Push", fromCsv.getTitle()); // tittelen fra CSV beholdes
        assertEquals(clientId, fromCsv.getClientId());
        assertEquals("Barbell Bench Press", fromCsv.getContent().path("blocks").get(0).path("name").asText());
        assertEquals(62.5, fromCsv.getContent().path("blocks").get(0).path("sets").get(1).path("weightKg").asDouble(), 0.001);
        assertEquals(300, fromCsv.getContent().path("kcal").asInt()); // CSV-feltene beholdes
        assertEquals(18, fromCsv.getContent().path("totalSets").asInt());
        assertEquals(118, fromCsv.getContent().path("avgHr").asInt()); // manglende felt fylles fra FIT
        verify(repo, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.argThat(w -> w != fromCsv));
    }

    @Test
    void withTwoCandidatesTheOneWithTheClosestDurationWins() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        Workout morning = csvStrength(day, "Pull", 30);
        Workout evening = csvStrength(day, "Legs", 70);
        when(repo.findByUserIdAndClientId(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        when(repo.findByUserIdAndDate(user, day)).thenReturn(List.of(morning, evening));

        service.importFit(user, List.of(fit(UUID.randomUUID(), day, 68.0, "Squat")));

        assertEquals("Squat", evening.getContent().path("blocks").get(0).path("name").asText());
        assertTrue(morning.getContent().path("blocks").isMissingNode());
    }

    @Test
    void createsANewWorkoutWhenNothingFromCsvMatches() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        when(repo.findByUserIdAndClientId(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        when(repo.findByUserIdAndDate(user, day)).thenReturn(List.of());
        ArgumentCaptor<Workout> saved = ArgumentCaptor.forClass(Workout.class);

        var r = service.importFit(user, List.of(fit(UUID.randomUUID(), day, 50.0, "Bicep Curls")));

        assertEquals(1, r.imported());
        verify(repo).save(saved.capture());
        assertEquals("Styrkeøkt", saved.getValue().getTitle());
        assertEquals("STYRKE", saved.getValue().getType());
        // øvelsen er koblet til katalogen, så progresjonen samles på tvers av språk
        assertEquals("bicep-curls", saved.getValue().getContent().path("blocks").get(0).path("exerciseId").asText());
    }

    @Test
    void reimportingTheSameFitSessionIsSkipped() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        UUID clientId = UUID.randomUUID();
        when(repo.findByUserIdAndClientId(user, clientId))
                .thenReturn(java.util.Optional.of(csvStrength(day, "Push", 52)));

        var r = service.importFit(user, List.of(fit(clientId, day, 51.0, "Barbell Bench Press")));

        assertEquals(1, r.skipped());
        verify(repo, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any(Workout.class));
    }

    @Test
    void workoutsThatAlreadyHaveExercisesAreNotOverwritten() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        Workout logged = csvStrength(day, "Push", 52);
        ((com.fasterxml.jackson.databind.node.ObjectNode) logged.getContent()).putArray("blocks").addObject().put("name", "Mine");
        when(repo.findByUserIdAndClientId(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
        when(repo.findByUserIdAndDate(user, day)).thenReturn(List.of(logged));

        var r = service.importFit(user, List.of(fit(UUID.randomUUID(), day, 51.0, "Barbell Bench Press")));

        assertEquals(1, r.imported()); // egen økt beholdes urørt, FIT-økta blir en ny
        assertEquals("Mine", logged.getContent().path("blocks").get(0).path("name").asText());
    }

    @Test
    void rejectsTooManySetsInOneRequestAndFutureDates() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        List<FitSession.SetEntry> hundred = java.util.Collections.nCopies(100, new FitSession.SetEntry(5, 50));
        List<FitSession.Block> blocks = java.util.Collections.nCopies(60, new FitSession.Block("Squat", hundred));
        List<FitSession> tooMany = java.util.Collections.nCopies(2, new FitSession(UUID.randomUUID(), day, null, 50.0, null, null, null, blocks));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.importFit(user, tooMany));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> service.importFit(user,
                List.of(fit(UUID.randomUUID(), LocalDate.now().plusYears(2), 50.0, "Squat"))));
    }

    // --- CSV etter FIT: samme økt skal ikke bli to ---

    @Test
    void csvRowIsMergedIntoAnAlreadyImportedFitSessionInsteadOfDuplicatingIt() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        var content = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        content.put("durationMin", 51);
        content.putArray("blocks").addObject().put("name", "Barbell Bench Press");
        Workout fromFit = new Workout(user, day, "Styrkeøkt", "STYRKE", content, "Importert fra Garmin (FIT)")
                .withOrigin(UUID.randomUUID(), null);
        when(repo.findKeysBetween(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(repo.findByUserIdAndTypeAndDateBetween(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq("STYRKE"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(fromFit));
        String csv = """
                Aktivitetstype,Dato,Tittel,Kalorier,Tid,Totalt antall sett
                Styrketrening,2026-06-30 17:00:00,"Push","310","00:52:10","18"
                """;

        var r = service.importGarmin(user, csv);

        assertEquals(0, r.imported());
        assertEquals(1, r.merged());
        assertEquals("Push", fromFit.getTitle()); // CSV-tittelen erstatter den generiske
        assertEquals(310, fromFit.getContent().path("kcal").asInt()); // totaler fylles inn
        assertEquals(18, fromFit.getContent().path("totalSets").asInt());
        assertEquals(51, fromFit.getContent().path("durationMin").asInt()); // FIT-verdier overstyres ikke
        assertEquals("Barbell Bench Press", fromFit.getContent().path("blocks").get(0).path("name").asText()); // øvelser urørt
        verify(repo, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.argThat(w -> w != fromFit));
    }

    @Test
    void aCsvRowWithVeryDifferentDurationIsNotMergedIntoAnotherSessionTheSameDay() {
        LocalDate day = LocalDate.of(2026, 6, 30);
        var content = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        content.put("durationMin", 20);
        Workout fromFit = new Workout(user, day, "Styrkeøkt", "STYRKE", content, "FIT").withOrigin(UUID.randomUUID(), null);
        when(repo.findKeysBetween(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(repo.findByUserIdAndTypeAndDateBetween(org.mockito.ArgumentMatchers.eq(user), org.mockito.ArgumentMatchers.eq("STYRKE"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(fromFit));
        String csv = """
                Aktivitetstype,Dato,Tittel,Tid
                Styrketrening,2026-06-30 19:00:00,"Legs","01:10:00"
                """;

        var r = service.importGarmin(user, csv);

        assertEquals(1, r.imported());
        assertEquals(0, r.merged());
        assertEquals("Styrkeøkt", fromFit.getTitle());
    }
}
