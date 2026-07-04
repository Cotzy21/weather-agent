package no.weatheragent.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.recovery.InjuryWarning.Level;
import no.weatheragent.training.Workout;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InjuryRiskAnalyzerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 4);

    /** 10 km løpetur = 750 kcal-last med nominell vekt (1 kcal/kg/km · 75 kg). */
    private static Workout run(LocalDate date) {
        try {
            return new Workout(USER, date, "Løpetur", "LØPING",
                    MAPPER.readTree("{\"distanceKm\":10}"), null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Én 10 km-tur per uke i de 4 «kroniske» ukene (dag 8-35 bakover). */
    private static List<Workout> stableHistory() {
        List<Workout> list = new ArrayList<>();
        for (int week = 1; week <= 4; week++) {
            list.add(run(TODAY.minusDays(7 * week)));
        }
        return list;
    }

    @Test
    void tripledRunningVolumeGivesHighWarning() {
        List<Workout> workouts = stableHistory();
        // Akutt uke: tre turer i stedet for én -> 2250/750 = 3.0.
        workouts.add(run(TODAY));
        workouts.add(run(TODAY.minusDays(2)));
        workouts.add(run(TODAY.minusDays(4)));

        List<InjuryWarning> warnings = InjuryRiskAnalyzer.analyze(workouts, TODAY);

        assertEquals(1, warnings.size());
        InjuryWarning w = warnings.getFirst();
        assertEquals("LØPING", w.type());
        assertEquals(Level.HOY, w.level());
        assertEquals(200, w.percentAboveNormal());
        assertTrue(w.message().contains("beinhinne"));
    }

    @Test
    void stableVolumeGivesNoWarning() {
        List<Workout> workouts = stableHistory();
        workouts.add(run(TODAY)); // samme som hver uke før -> ratio 1.0

        assertTrue(InjuryRiskAnalyzer.analyze(workouts, TODAY).isEmpty());
    }

    @Test
    void activityWithoutHistoryIsFlaggedAsNew() {
        // 800+ kcal løping denne uka, ingen historikk.
        List<InjuryWarning> warnings = InjuryRiskAnalyzer.analyze(
                List.of(run(TODAY), run(TODAY.minusDays(3))), TODAY);

        assertEquals(1, warnings.size());
        assertEquals(Level.NY_AKTIVITET, warnings.getFirst().level());
        assertTrue(warnings.getFirst().message().contains("gradvis"));
    }

    @Test
    void tinyLoadsAreIgnored() throws Exception {
        // 20 min fristil ≈ 125 kcal - under terskelen, selv uten historikk.
        Workout small = new Workout(USER, TODAY, "Tøying", "FRISTIL",
                MAPPER.readTree("{\"durationMin\":20}"), null);

        assertTrue(InjuryRiskAnalyzer.analyze(List.of(small), TODAY).isEmpty());
    }

    @Test
    void moderateIncreaseGivesModerateWarning() {
        List<Workout> workouts = stableHistory();
        workouts.add(run(TODAY));
        // + en liten ekstra økt (~300 kcal): (750+300)/750 = 1.4 -> MODERAT.
        try {
            workouts.add(new Workout(USER, TODAY.minusDays(1), "Kort tur", "LØPING",
                    MAPPER.readTree("{\"distanceKm\":4}"), null));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        List<InjuryWarning> warnings = InjuryRiskAnalyzer.analyze(workouts, TODAY);

        assertEquals(1, warnings.size());
        assertEquals(Level.MODERAT, warnings.getFirst().level());
        assertEquals(40, warnings.getFirst().percentAboveNormal());
    }
}
