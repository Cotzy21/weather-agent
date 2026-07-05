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
    void planerRequireAuthentication() throws Exception {
        mvc.perform(get("/api/trening/planer")).andExpect(status().isUnauthorized());
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
