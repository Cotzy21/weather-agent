package no.weatheragent.web;

import no.weatheragent.security.SecurityConfig;
import no.weatheragent.training.TrainingPlanService;
import no.weatheragent.training.WorkoutImportService;
import no.weatheragent.training.WorkoutService;
import no.weatheragent.training.WorkoutSuggester;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrainingController.class)
@Import(SecurityConfig.class)
class TrainingControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkoutService workouts;

    @MockitoBean
    private WorkoutSuggester suggester;

    @MockitoBean
    private TrainingPlanService plans;

    @MockitoBean
    private WorkoutImportService importer;

    @MockitoBean
    private no.weatheragent.training.ReadinessService readiness;

    @MockitoBean
    private no.weatheragent.training.TrainingMemoryService memory;

    @MockitoBean
    private no.weatheragent.training.TrainingProfileService profiles;

    @MockitoBean
    private no.weatheragent.training.AiRateLimiter aiLimit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/treningsokter")).andExpect(status().isUnauthorized());
    }

    @Test
    void progressionRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/ovelser/progresjon").param("navn", "Benkpress"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listWorksWhenAuthenticated() throws Exception {
        when(workouts.listFor(any())).thenReturn(List.of());
        mvc.perform(get("/api/treningsokter")
                        .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
                .andExpect(status().isOk());
    }

    @Test
    void memoryRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/trening/minne")).andExpect(status().isUnauthorized());
    }

    @Test
    void profileRequiresAuthAndIsEmptyBeforeOnboarding() throws Exception {
        mvc.perform(get("/api/trening/profil")).andExpect(status().isUnauthorized());

        when(profiles.find(any())).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/api/trening/profil")
                        .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void aiEndpointReturns429WhenTheUserIsOverTheLimit() throws Exception {
        org.mockito.Mockito.doThrow(new no.weatheragent.training.AiRateLimiter.LimitExceededException("For mange kall", 120))
                .when(aiLimit).check(any());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/trening/forslag")
                        .contentType("application/json").content("{\"focus\":\"bein\"}")
                        .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "120"));
    }

    @Test
    void planerRequireAuthentication() throws Exception {
        mvc.perform(get("/api/trening/planer")).andExpect(status().isUnauthorized());
    }

    @Test
    void readinessRequiresAuthAndIsEmptyWithoutSleepData() throws Exception {
        mvc.perform(get("/api/trening/dagsform")).andExpect(status().isUnauthorized());

        when(readiness.today(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(java.util.Optional.empty());
        mvc.perform(get("/api/trening/dagsform")
                        .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void readinessIsReturnedWhenSleepIsLogged() throws Exception {
        when(readiness.today(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(java.util.Optional.of(
                new no.weatheragent.training.Readiness(no.weatheragent.training.Readiness.Level.LAV,
                        java.time.LocalDate.of(2026, 9, 29), 5.0, 5.5, "Lite søvn – velg en lettere økt.")));

        mvc.perform(get("/api/trening/dagsform")
                        .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.level").value("LAV"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.lastNightHours").value(5.0));
    }

    @Test
    void nextSessionRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/ovelser/neste")).andExpect(status().isUnauthorized());
    }

    @Test
    void nextSessionReturnsSuggestionsWithLanguage() throws Exception {
        when(workouts.nextSession(any(), any(), org.mockito.ArgumentMatchers.eq(true))).thenReturn(List.of(
                new no.weatheragent.training.NextSetSuggestion("Benkpress", java.time.LocalDate.of(2026, 9, 26),
                        80, List.of(5, 5, 5), no.weatheragent.training.NextSetSuggestion.Action.OK_VEKT,
                        82.5, 3, 3, "Every set hit 5 reps last time – go up to 82.5 kg")));

        mvc.perform(get("/api/ovelser/neste").param("lang", "en")
                        .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].action").value("OK_VEKT"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].nextWeightKg").value(82.5))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].lastReps[2]").value(5));
    }

    @Test
    void planSuggestionRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/trening/plan-forslag")
                        .contentType("application/json").content("{\"focus\":\"ppl split\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assistantRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/trening/assistent")
                        .contentType("application/json")
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"lag et program\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garminImportRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/trening/import/garmin").contentType("text/plain").content("a,b"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void planListWorksWhenAuthenticated() throws Exception {
        when(plans.listFor(any())).thenReturn(List.of());
        mvc.perform(get("/api/trening/planer")
                        .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))))
                .andExpect(status().isOk());
    }
}
