package no.weatheragent.advice;

import no.weatheragent.advice.RouteAssessment.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteAssessorTest {

    @Test
    void shortFlatTripIsGreen() {
        // 4 km, nesten flatt (10 m variasjon over 9 segmenter).
        double[] profile = {100, 102, 105, 108, 110, 108, 105, 103, 100, 100};

        RouteAssessment a = RouteAssessor.assess(4, profile);

        assertEquals(Difficulty.GRONN, a.difficulty());
        assertTrue(a.challenges().isEmpty());
        assertEquals(110, a.highestPointM());
    }

    @Test
    void worstOfTheThreeGradesWins() {
        // Kort (grønn på distanse) men 700 m stigning -> RØD på stigning.
        double[] profile = {100, 300, 500, 800};

        RouteAssessment a = RouteAssessor.assess(4.5, profile);

        assertEquals(Difficulty.ROED, a.difficulty());
    }

    @Test
    void steepSegmentAloneBumpsTheGrade() {
        // 2 km, lite total stigning, men ett segment på 200 m over 500 m = 40 %.
        double[] profile = {100, 100, 300, 300, 300};

        RouteAssessment a = RouteAssessor.assess(2, profile);

        assertEquals(Difficulty.SVART, a.difficulty());
        assertEquals(40, a.maxGradientPct(), 0.1);
    }

    @Test
    void mountainRouteWarnsAboutWeatherAndSteepness() {
        // Opp til 1400 moh, og ett segment på 350 m over 2 km = 17.5 % helning.
        double[] profile = {400, 600, 950, 1100, 1300, 1400};

        RouteAssessment a = RouteAssessor.assess(10, profile);

        assertTrue(a.challenges().stream().anyMatch(c -> c.contains("moh")));
        assertTrue(a.challenges().stream().anyMatch(c -> c.contains("Bratte partier")));
        assertTrue(a.challenges().stream().anyMatch(c -> c.contains("stigning")));
    }

    @Test
    void longDescentWarnsAboutKnees() {
        // 18 km: 1000 m jevnt ned -> nedstignings- og langtur-råd.
        double[] profile = {1200, 1000, 800, 600, 400, 200};

        RouteAssessment a = RouteAssessor.assess(18, profile);

        assertEquals(1000, a.descentM());
        assertTrue(a.challenges().stream().anyMatch(c -> c.contains("nedstigning")));
        assertTrue(a.challenges().stream().anyMatch(c -> c.contains("Lang tur")));
    }

    @Test
    void emptyProfileGivesSafeDefaults() {
        RouteAssessment a = RouteAssessor.assess(3, new double[0]);

        assertEquals(Difficulty.GRONN, a.difficulty());
        assertEquals(0, a.maxGradientPct());
    }
}
