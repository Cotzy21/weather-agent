package no.weatheragent.advice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClothingAdvisorTest {

    private static boolean any(List<String> tips, String needle) {
        return tips.stream().anyMatch(t -> t.toLowerCase().contains(needle.toLowerCase()));
    }

    @Test
    void coldWeatherSuggestsWinterClothing() {
        List<String> tips = ClothingAdvisor.recommend(-3, 0, 2);
        assertTrue(any(tips, "vinterklær"), tips.toString());
    }

    @Test
    void warmAndDrySuggestsSunProtection() {
        List<String> tips = ClothingAdvisor.recommend(24, 0, 1);
        assertTrue(any(tips, "solkrem"), tips.toString());
        assertFalse(any(tips, "regntøy"), tips.toString());
    }

    @Test
    void heavyRainSuggestsRainGear() {
        List<String> tips = ClothingAdvisor.recommend(12, 8, 2);
        assertTrue(any(tips, "regntøy"), tips.toString());
        assertFalse(any(tips, "solkrem"), tips.toString());
    }

    @Test
    void strongWindSuggestsWindproofLayer() {
        List<String> tips = ClothingAdvisor.recommend(10, 0, 12);
        assertTrue(any(tips, "vindtett"), tips.toString());
    }

    @Test
    void footwearAndWaterAlwaysIncluded() {
        assertTrue(any(ClothingAdvisor.recommend(20, 0, 1), "tursko"));
        assertTrue(any(ClothingAdvisor.recommend(-10, 20, 15), "tursko"));
    }
}
