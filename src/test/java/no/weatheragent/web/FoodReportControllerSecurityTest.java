package no.weatheragent.web;

import no.weatheragent.nutrition.CustomFood;
import no.weatheragent.nutrition.FoodReportService;
import no.weatheragent.nutrition.ReportReason;
import no.weatheragent.security.AdminGuard;
import no.weatheragent.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FoodReportController.class)
@Import({SecurityConfig.class, AdminGuard.class})
@TestPropertySource(properties = "app.admin.user-ids=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
class FoodReportControllerSecurityTest {

    private static final String ADMIN = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String USER = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final UUID FOOD = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FoodReportService reports;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void reportingRequiresLogin() throws Exception {
        mvc.perform(post("/api/kosthold/egne-matvarer/" + FOOD + "/rapport")
                        .contentType("application/json").content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reports);
    }

    @Test
    void aLoggedInUserCanReportAFoodAndTheReporterIsTakenFromTheToken() throws Exception {
        mvc.perform(post("/api/kosthold/egne-matvarer/" + FOOD + "/rapport")
                        .with(jwt().jwt(j -> j.subject(USER)))
                        .contentType("application/json").content("{\"reason\":\"WRONG_VALUES\",\"note\":\"feil kcal\"}"))
                .andExpect(status().isNoContent());

        verify(reports).report(UUID.fromString(USER), FOOD, ReportReason.WRONG_VALUES, "feil kcal");
    }

    @Test
    void unknownReasonsAndOverlongNotesAreRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/kosthold/egne-matvarer/" + FOOD + "/rapport")
                        .with(jwt().jwt(j -> j.subject(USER)))
                        .contentType("application/json").content("{\"reason\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/kosthold/egne-matvarer/" + FOOD + "/rapport")
                        .with(jwt().jwt(j -> j.subject(USER)))
                        .contentType("application/json").content("{\"reason\":\"SPAM\",\"note\":\"" + "x".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/kosthold/egne-matvarer/" + FOOD + "/rapport")
                        .with(jwt().jwt(j -> j.subject(USER)))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }

    @Test
    void serviceRulesSurfaceAsA400WithTheMessage() throws Exception {
        doThrow(new IllegalArgumentException("Du kan ikke rapportere din egen matvare."))
                .when(reports).report(any(), any(), any(), any());

        mvc.perform(post("/api/kosthold/egne-matvarer/" + FOOD + "/rapport")
                        .with(jwt().jwt(j -> j.subject(USER)))
                        .contentType("application/json").content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Du kan ikke rapportere din egen matvare."));
    }

    @Test
    void adminEndpointsRequireLogin() throws Exception {
        mvc.perform(get("/api/admin/rapporter")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/matvarer/" + FOOD)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/rapporter/" + FOOD)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reports);
    }

    @Test
    void adminEndpointsAreForbiddenForOrdinaryUsers() throws Exception {
        mvc.perform(get("/api/admin/rapporter").with(jwt().jwt(j -> j.subject(USER)))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/matvarer/" + FOOD).with(jwt().jwt(j -> j.subject(USER)))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/rapporter/" + FOOD).with(jwt().jwt(j -> j.subject(USER)))).andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }

    @Test
    void anAdminSeesTheReportedFoods() throws Exception {
        CustomFood food = new CustomFood(UUID.randomUUID(), "Proteinbar", "Merke", null, 350, 30, 10, 40, null, null, true);
        ReflectionTestUtils.setField(food, "id", FOOD); // ikke lagret i DB i denne testen, så id settes for hånd
        Map<ReportReason, Long> reasons = new EnumMap<>(ReportReason.class);
        reasons.put(ReportReason.SPAM, 2L);
        when(reports.listReported()).thenReturn(List.of(
                new FoodReportService.ReportedFood(food, 2, Instant.parse("2026-09-30T12:00:00Z"), reasons, List.of("suspekt"))));

        mvc.perform(get("/api/admin/rapporter").with(jwt().jwt(j -> j.subject(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(FOOD.toString()))
                .andExpect(jsonPath("$[0].name").value("Proteinbar"))
                .andExpect(jsonPath("$[0].reports").value(2))
                .andExpect(jsonPath("$[0].reasons.SPAM").value(2))
                .andExpect(jsonPath("$[0].notes[0]").value("suspekt"));
    }

    @Test
    void anAdminCanDeleteAFoodOrDismissItsReports() throws Exception {
        when(reports.deleteFood(FOOD)).thenReturn(true);

        mvc.perform(delete("/api/admin/matvarer/" + FOOD).with(jwt().jwt(j -> j.subject(ADMIN)))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/rapporter/" + FOOD).with(jwt().jwt(j -> j.subject(ADMIN)))).andExpect(status().isNoContent());

        verify(reports).deleteFood(FOOD);
        verify(reports).dismiss(FOOD);
    }

    @Test
    void deletingAFoodThatDoesNotExistGives404() throws Exception {
        when(reports.deleteFood(FOOD)).thenReturn(false);

        mvc.perform(delete("/api/admin/matvarer/" + FOOD).with(jwt().jwt(j -> j.subject(ADMIN)))).andExpect(status().isNotFound());
    }
}
