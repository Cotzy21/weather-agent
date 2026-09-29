package no.weatheragent.web;

import no.weatheragent.nutrition.FoodItem;
import no.weatheragent.nutrition.FoodSearchService;
import no.weatheragent.nutrition.MealEntry;
import no.weatheragent.nutrition.MealLogService;
import no.weatheragent.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
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
    private no.weatheragent.nutrition.CustomFoodService customFoods;

    @MockitoBean
    private no.weatheragent.nutrition.DietPreferenceService preferences;

    @BeforeEach
    void noPreferencesByDefault() {
        when(preferences.profileFor(any())).thenReturn(no.weatheragent.nutrition.DietProfile.NONE);
    }

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

        when(meals.week(eq(UUID.fromString(SUB)), any(), any())).thenReturn(List.of(lowCalcium));

        mvc.perform(get("/api/kosthold/uke").param("til", "2026-07-02")
                        .with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kalsium"))
                .andExpect(jsonPath("$[0].advice").isNotEmpty());
    }

    @Test
    void customFoodsComeFirstInSearchAndAreLabeledBySource() throws Exception {
        UUID me = UUID.fromString(SUB);
        var mine = new no.weatheragent.nutrition.CustomFood(me, "Proteinshake", null, null,
                380, 75, 5, 8, null, null, false);
        var shared = new no.weatheragent.nutrition.CustomFood(UUID.randomUUID(), "Protein bar", null, null,
                350, 30, 10, 40, null, null, true);
        when(customFoods.search(me, "prot")).thenReturn(List.of(mine, shared));
        when(search.search("prot")).thenReturn(List.of(new FoodItem(
                "01.001", "Proteinpulver", 370, Map.of("Protein", 80.0), List.of(), List.of())));

        mvc.perform(get("/api/kosthold/matvarer").param("sok", "prot")
                        .with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("EGEN"))
                .andExpect(jsonPath("$[1].source").value("OFFENTLIG"))
                .andExpect(jsonPath("$[2].source").value("MATVARETABELLEN"));
    }

    @Test
    void unknownBarcodeGives404() throws Exception {
        when(customFoods.byBarcode(UUID.fromString(SUB), "7038010009457")).thenReturn(java.util.Optional.empty());

        mvc.perform(get("/api/kosthold/strekkode/7038010009457").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void customFoodEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/kosthold/egne-matvarer")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/kosthold/egne-matvarer")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/kosthold/strekkode/123")).andExpect(status().isUnauthorized());
    }

    @Test
    void searchFlagsAndDemotesFoodsAgainstThePreferences() throws Exception {
        UUID me = UUID.fromString(SUB);
        when(preferences.profileFor(me)).thenReturn(new no.weatheragent.nutrition.DietProfile(
                no.weatheragent.nutrition.FoodFlags.Diet.ALT,
                java.util.Set.of(no.weatheragent.nutrition.FoodFlags.Allergen.MELK), List.of()));
        when(customFoods.search(me, "drikk")).thenReturn(List.of());
        when(search.search("drikk")).thenReturn(List.of(
                new FoodItem("1", "Melkedrikk, sjokolade", 70, Map.of(), List.of(), List.of()),
                new FoodItem("2", "Havredrikk", 45, Map.of(), List.of(), List.of())));

        mvc.perform(get("/api/kosthold/matvarer").param("sok", "drikk")
                        .with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Havredrikk"))   // uten advarsel først
                .andExpect(jsonPath("$[0].warnings").isEmpty())
                .andExpect(jsonPath("$[1].warnings[0]").value("ALLERGEN:MELK"));
    }

    @Test
    void preferencesRoundTripAndRequireAuthentication() throws Exception {
        mvc.perform(get("/api/kosthold/preferanser")).andExpect(status().isUnauthorized());

        when(preferences.save(eq(UUID.fromString(SUB)), eq("VEGAN"), any(), any()))
                .thenReturn(new no.weatheragent.nutrition.DietProfile(
                        no.weatheragent.nutrition.FoodFlags.Diet.VEGAN,
                        java.util.Set.of(no.weatheragent.nutrition.FoodFlags.Allergen.SESAM), List.of("sopp")));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/kosthold/preferanser")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diet\":\"VEGAN\",\"allergies\":[\"SESAM\"],\"dislikes\":[\"sopp\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diet").value("VEGAN"))
                .andExpect(jsonPath("$.allergies[0]").value("SESAM"))
                .andExpect(jsonPath("$.dislikes[0]").value("sopp"));
    }
}
