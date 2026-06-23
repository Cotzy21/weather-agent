package no.weatheragent.advice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalorieAdvisorTest {

    @Test
    void longerWalkBurnsMore() {
        int shortWalk = CalorieAdvisor.estimate(5, 0, 75).calories();
        int longWalk = CalorieAdvisor.estimate(15, 0, 75).calories();

        assertTrue(longWalk > shortWalk, "lengre tur skal gi flere kalorier");
    }

    @Test
    void ascentAddsCaloriesAndTime() {
        RouteEstimate flat = CalorieAdvisor.estimate(10, 0, 75);
        RouteEstimate hilly = CalorieAdvisor.estimate(10, 800, 75);

        assertTrue(hilly.calories() > flat.calories(), "stigning skal koste mer");
        assertTrue(hilly.hours() > flat.hours(), "stigning skal ta lengre tid");
    }

    @Test
    void heavierHikerBurnsMore() {
        assertTrue(CalorieAdvisor.estimate(10, 300, 95).calories()
                > CalorieAdvisor.estimate(10, 300, 60).calories());
    }

    @Test
    void zeroOrNegativeWeightFallsBackToDefault() {
        // skal ikke kaste eller gi 0/negativt
        assertTrue(CalorieAdvisor.estimate(10, 0, 0).calories() > 0);
        assertTrue(CalorieAdvisor.estimate(10, 0, -5).calories() > 0);
    }

    @Test
    void snacksAlwaysIncludeWater() {
        assertTrue(CalorieAdvisor.estimate(0, 0, 75).snacks().stream()
                .anyMatch(s -> s.toLowerCase().contains("vann")));
    }

    @Test
    void roundsDistanceAndHours() {
        RouteEstimate e = CalorieAdvisor.estimate(12.345, 123.4, 75);
        assertEquals(12.3, e.distanceKm());
        assertEquals(123.0, e.ascentM());
    }
}
