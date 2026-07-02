package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MealLogServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 7, 2);

    // Melk: 47 kcal, 3.5 g protein, 124 mg kalsium per 100 g.
    private static final FoodItem MELK = new FoodItem("01.291", "Melk", 47,
            Map.of("Protein", 3.5, "Fett", 1.5, "Karbo", 4.6, "Ca", 124.0),
            List.of(), List.of());

    private final MealEntryRepository repository = mock(MealEntryRepository.class);
    private final MatvaretabellenClient client = mock(MatvaretabellenClient.class);
    private final MealLogService service = new MealLogService(repository, client);

    @Test
    void loggingSnapshotsCaloriesAndMacrosScaledByGrams() {
        when(client.allFoods()).thenReturn(List.of(MELK));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Ett glass = 200 g -> dobbelt av per-100g-verdiene.
        MealEntry entry = service.log(USER, DATE, "FROKOST", "01.291", 200);

        assertEquals(94, entry.getKcal());
        assertEquals(7.0, entry.getProteinG());
        assertEquals("Melk", entry.getFoodName());
    }

    @Test
    void unknownFoodIsRejected() {
        when(client.allFoods()).thenReturn(List.of(MELK));

        assertThrows(UnknownFoodException.class,
                () -> service.log(USER, DATE, "FROKOST", "99.999", 100));
    }

    @Test
    void unknownMealIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.log(USER, DATE, "BRUNSJ", "01.291", 100));
    }

    @Test
    void daySumsAllEntries() {
        when(repository.findByUserIdAndDateOrderByCreatedAtAsc(USER, DATE)).thenReturn(List.of(
                new MealEntry(USER, DATE, "FROKOST", "1", "A", 100, 100, 10, 5, 20),
                new MealEntry(USER, DATE, "MIDDAG", "2", "B", 100, 400, 30, 15, 40)));

        MealLogService.DaySummary day = service.day(USER, DATE);

        assertEquals(500, day.kcal());
        assertEquals(40, day.proteinG());
        assertEquals(2, day.entries().size());
    }

    @Test
    void weekAveragesPerLoggedDayAndFlagsLowIntake() {
        // To dager logget: 200 g melk hver dag -> 248 mg kalsium/dag av mål 950 -> lavt.
        when(client.allFoods()).thenReturn(List.of(MELK));
        when(repository.findByUserIdAndDateBetween(any(), any(), any())).thenReturn(List.of(
                new MealEntry(USER, DATE.minusDays(1), "FROKOST", "01.291", "Melk", 200, 94, 7, 3, 9.2),
                new MealEntry(USER, DATE, "FROKOST", "01.291", "Melk", 200, 94, 7, 3, 9.2)));

        List<MealLogService.NutrientStatus> week = service.week(USER, DATE);

        MealLogService.NutrientStatus calcium = week.stream()
                .filter(s -> s.reference().nutrientId().equals("Ca"))
                .findFirst().orElseThrow();
        assertEquals(248, calcium.avgPerDay(), 0.01);
        assertTrue(calcium.percent() < 30);
        assertNotNull(calcium.advice()); // lavt -> råd om gode kilder
    }

    @Test
    void goodCoverageGivesNoAdvice() {
        // 3000 g melk på én dag -> 3720 mg kalsium -> godt over målet.
        when(client.allFoods()).thenReturn(List.of(MELK));
        when(repository.findByUserIdAndDateBetween(any(), any(), any())).thenReturn(List.of(
                new MealEntry(USER, DATE, "FROKOST", "01.291", "Melk", 3000, 1410, 105, 45, 138)));

        MealLogService.NutrientStatus calcium = service.week(USER, DATE).stream()
                .filter(s -> s.reference().nutrientId().equals("Ca"))
                .findFirst().orElseThrow();

        assertNull(calcium.advice());
    }

    @Test
    void emptyWeekGivesEmptyList() {
        when(repository.findByUserIdAndDateBetween(any(), any(), any())).thenReturn(List.of());

        assertTrue(service.week(USER, DATE).isEmpty());
    }
}
