package no.weatheragent.web;

import no.weatheragent.security.SecurityConfig;
import no.weatheragent.training.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettingsController.class)
@Import(SecurityConfig.class)
class SettingsControllerSecurityTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserSettingsService settings;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void requireAuthentication() throws Exception {
        mvc.perform(get("/api/innstillinger/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/trening/program")).andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardIsEmptyUntilSaved() throws Exception {
        when(settings.get(any(), eq("dashboard"))).thenReturn(Optional.empty());
        mvc.perform(get("/api/innstillinger/dashboard").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isNoContent());
    }

    @Test
    void onlyDashboardAndStreakAreExposedAsGenericSettings() throws Exception {
        mvc.perform(get("/api/innstillinger/program").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/innstillinger/noe-annet").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/innstillinger/program").with(jwt().jwt(j -> j.subject(USER)))
                        .contentType("application/json").content("{\"startDate\":\"tull\",\"weeks\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theStreakSettingIsEmptyUntilSavedAndThenReturnsWhatWasStored() throws Exception {
        when(settings.get(any(), eq("streak"))).thenReturn(Optional.empty());
        mvc.perform(get("/api/innstillinger/streak").with(jwt().jwt(j -> j.subject(USER))))
                .andExpect(status().isNoContent());

        var stored = new com.fasterxml.jackson.databind.ObjectMapper().readTree("{\"goal\":4,\"pauses\":[\"2026-09-28\"]}");
        when(settings.put(any(), eq("streak"), any())).thenReturn(stored);
        mvc.perform(put("/api/innstillinger/streak").with(jwt().jwt(j -> j.subject(USER)))
                        .contentType("application/json").content("{\"goal\":4,\"pauses\":[\"2026-09-28\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").value(4))
                .andExpect(jsonPath("$.pauses[0]").value("2026-09-28"));
    }

    @Test
    void theStreakSettingRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/innstillinger/streak")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/innstillinger/streak").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startingAProgramReturnsProgress() throws Exception {
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Oslo")).toString();
        mvc.perform(put("/api/trening/program").with(jwt().jwt(j -> j.subject(USER)))
                        .contentType("application/json").content("{\"startDate\":\"" + today + "\",\"weeks\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayNumber").value(1))
                .andExpect(jsonPath("$.totalDays").value(56));
    }
}
