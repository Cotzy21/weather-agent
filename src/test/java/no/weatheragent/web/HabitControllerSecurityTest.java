package no.weatheragent.web;

import no.weatheragent.habit.Habit;
import no.weatheragent.habit.HabitLog;
import no.weatheragent.habit.HabitService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sikkerhet + kontrakt for habit-API-et, uten DB (mock service). */
@WebMvcTest(HabitController.class)
@Import(SecurityConfig.class)
class HabitControllerSecurityTest {

    private static final String SUB = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HabitService habitService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void vanerRequireAuthentication() throws Exception {
        mvc.perform(get("/api/vaner")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/vaner")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/vaner/" + UUID.randomUUID() + "/logg")).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsHabitsWithLogsAndStreak() throws Exception {
        Habit habit = new Habit(UUID.fromString(SUB), "Koffein", "☕", "kopper");
        when(habitService.listFor(eq(UUID.fromString(SUB)), any())).thenReturn(List.of(
                new HabitService.HabitWithLogs(habit,
                        List.of(new HabitLog(habit.getId(), LocalDate.of(2026, 7, 4), 3)), 5)));

        mvc.perform(get("/api/vaner").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Koffein"))
                .andExpect(jsonPath("$[0].unit").value("kopper"))
                .andExpect(jsonPath("$[0].streakDays").value(5))
                .andExpect(jsonPath("$[0].logs[0].value").value(3.0));
    }

    @Test
    void logDelegatesToServiceWithOwnUser() throws Exception {
        UUID habitId = UUID.randomUUID();

        mvc.perform(put("/api/vaner/" + habitId + "/logg")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-07-04\",\"value\":3}"))
                .andExpect(status().isNoContent());

        verify(habitService).log(habitId, UUID.fromString(SUB), LocalDate.of(2026, 7, 4), 3);
    }

    @Test
    void invalidCreateIsRejected() throws Exception {
        mvc.perform(post("/api/vaner")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
