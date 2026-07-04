package no.weatheragent.web;

import no.weatheragent.recovery.InjuryWarning;
import no.weatheragent.recovery.RecoveryAdvice;
import no.weatheragent.recovery.RecoveryService;
import no.weatheragent.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sikkerhet + kontrakt for restitusjons-API-et, uten DB (mock service). */
@WebMvcTest(RecoveryController.class)
@Import(SecurityConfig.class)
class RecoveryControllerSecurityTest {

    private static final String SUB = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RecoveryService recovery;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void recoveryRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/recovery")).andExpect(status().isUnauthorized());
    }

    @Test
    void reportIsReturnedForAuthenticatedUser() throws Exception {
        when(recovery.report(UUID.fromString(SUB))).thenReturn(new RecoveryService.RecoveryReport(
                List.of(new RecoveryAdvice("LØPING", "i dag", List.of("Karbohydrater og væske"))),
                List.of(new InjuryWarning("LØPING", InjuryWarning.Level.HOY, 200, "Ro ned denne uka.")),
                4, true));

        mvc.perform(get("/api/recovery").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advice[0].type").value("LØPING"))
                .andExpect(jsonPath("$.advice[0].when").value("i dag"))
                .andExpect(jsonPath("$.warnings[0].level").value("HOY"))
                .andExpect(jsonPath("$.warnings[0].percentAboveNormal").value(200))
                .andExpect(jsonPath("$.streakDays").value(4))
                .andExpect(jsonPath("$.restDaySuggested").value(true));
    }
}
