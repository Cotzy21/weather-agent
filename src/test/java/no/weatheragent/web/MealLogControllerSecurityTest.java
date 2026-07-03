package no.weatheragent.web;

import no.weatheragent.nutrition.FoodItem;
import no.weatheragent.nutrition.FoodSearchService;
import no.weatheragent.nutrition.MealEntry;
import no.weatheragent.nutrition.MealLogService;
import no.weatheragent.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sikkerhet + kontrakt for kostholdsdagbok-API-et, uten DB (mock services).
 */
@WebMvcTest(MealLogController.class)
@Import(SecurityConfig.class)
class MealLogControllerSecurityTest {

    private static final String SUB = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FoodSearchService search;

    @MockitoBean
    private MealLogService meals;

    @MockitoBean
    private no.weatheragent.nutrition.DailyBalanceService balance;

    @MockitoBean
    private no.weatheragent.nutrition.CalorieGoalService goals;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void dagbokRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/kosthold/matvarer").param("sok", "melk")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/kosthold/dag").param("dato", "2026-07-02")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/kosthold/uke").param("til", "2026-07-02")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/kosthold/maal")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/kosthold/logg")).andExpect(status().isUnauthorized());
    }

    @Test
    void dayIncludesTrainingBalance() throws Exception {
        when(balance.day(eq(UUID.fromString(SUB)), eq(LocalDate.of(2026, 7, 2))))
                .thenReturn(new no.weatheragent.nutrition.DailyBalanceService.DayBalance(
                        new MealLogService.DaySummary(List.of(), 1500, 100, 50, 150),
                        800, 2800.0, 2100.0, "Stor treningsdag ..."));

        mvc.perform(get("/api/kosthold/dag").param("dato", "2026-07-02")
                        .with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kcal").value(1500.0))
                .andExpect(jsonPath("$.burnedKcal").value(800))
                .andExpect(jsonPath("$.remainingKcal").value(2100.0))
                .andExpect(jsonPath("$.recoveryTip").isNotEmpty());
    }

    @Test
    void goalReturns404UntilConfigured() throws Exception {
        when(goals.find(UUID.fromString(SUB))).thenReturn(java.util.Optional.empty());

        mvc.perform(get("/api/kosthold/maal").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchReturnsFoodsWithPortions() throws Exception {
        when(search.search("melk")).thenReturn(List.of(new FoodItem(
                "01.291", "Melk, uspesifisert", 47,
                Map.of("Protein", 3.5),
                List.of(new FoodItem.Portion("glass", "stk", 200.0)),
                List.of())));

        mvc.perform(get("/api/kosthold/matvarer").param("sok", "melk")
                        .with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].foodId").value("01.291"))
                .andExpect(jsonPath("$[0].proteinPer100g").value(3.5))
                .andExpect(jsonPath("$[0].portions[0].name").value("glass"))
                .andExpect(jsonPath("$[0].portions[0].grams").value(200.0));
    }

    @Test
    void logStoresForAuthenticatedUser() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 2);
        when(meals.log(eq(UUID.fromString(SUB)), eq(date), eq("FROKOST"), eq("01.291"), eq(200.0)))
                .thenReturn(new MealEntry(UUID.fromString(SUB), date, "FROKOST", "01.291", "Melk",
                        200, 94, 7, 3, 9.2));

        mvc.perform(post("/api/kosthold/logg")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-07-02","meal":"frokost","foodId":"01.291","grams":200}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foodName").value("Melk"))
                .andExpect(jsonPath("$.kcal").value(94.0));
    }

    @Test
    void invalidBodyIsRejected() throws Exception {
        mvc.perform(post("/api/kosthold/logg")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-07-02\",\"meal\":\"\",\"foodId\":\"x\",\"grams\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weekReturnsAdviceOnlyWhenLow() throws Exception {
        var lowCalcium = new MealLogService.NutrientStatus(
                no.weatheragent.nutrition.NutrientReference.TRACKED.stream()
                        .filter(r -> r.nutrientId().equals("Ca")).findFirst().orElseThrow(),
                248, 26.1);

        when(meals.week(eq(UUID.fromString(SUB)), any())).thenReturn(List.of(lowCalcium));

        mvc.perform(get("/api/kosthold/uke").param("til", "2026-07-02")
                        .with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kalsium"))
                .andExpect(jsonPath("$[0].advice").isNotEmpty());
    }
}
