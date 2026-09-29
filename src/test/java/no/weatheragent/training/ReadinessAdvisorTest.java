package no.weatheragent.training;

import no.weatheragent.training.Readiness.Level;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadinessAdvisorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    @Test
    void wellRestedIsGood() {
        Readiness r = ReadinessAdvisor.assess(Map.of(
                TODAY, 8.0, TODAY.minusDays(1), 7.5, TODAY.minusDays(2), 7.0), TODAY, false).orElseThrow();

        assertEquals(Level.GOD, r.level());
        assertEquals(7.5, r.avg3Hours(), 0.001);
    }

    @Test
    void oneShortNightIsLowEvenIfTheAverageIsFine() {
        Readiness r = ReadinessAdvisor.assess(Map.of(
                TODAY, 5.0, TODAY.minusDays(1), 8.5, TODAY.minusDays(2), 8.5), TODAY, false).orElseThrow();

        assertEquals(Level.LAV, r.level());
        assertTrue(r.advice().contains("lettere økt"));
    }

    @Test
    void accumulatedSleepDebtIsLow() {
        // 6,5 i natt er greit alene, men snittet 5,8 er for lavt.
        Readiness r = ReadinessAdvisor.assess(Map.of(
                TODAY, 6.5, TODAY.minusDays(1), 5.5, TODAY.minusDays(2), 5.5), TODAY, false).orElseThrow();

        assertEquals(Level.LAV, r.level());
    }

    @Test
    void inBetweenIsMedium() {
        Readiness r = ReadinessAdvisor.assess(Map.of(TODAY, 6.5), TODAY, true).orElseThrow();

        assertEquals(Level.MIDDELS, r.level());
        assertTrue(r.advice().startsWith("A bit short on sleep"));
    }

    @Test
    void fallsBackToYesterdayButNotFurther() {
        assertEquals(TODAY.minusDays(1),
                ReadinessAdvisor.assess(Map.of(TODAY.minusDays(1), 7.5), TODAY, false).orElseThrow().nightDate());
        assertTrue(ReadinessAdvisor.assess(Map.of(TODAY.minusDays(2), 7.5), TODAY, false).isEmpty());
    }
}
