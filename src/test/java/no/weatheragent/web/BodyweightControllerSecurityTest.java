package no.weatheragent.web;

import no.weatheragent.body.WeighInService;
import no.weatheragent.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

/** Kroppsvekt-API-et skal kreve innlogging (stien må stå i SecurityConfig). */
@WebMvcTest(BodyweightController.class)
@Import(SecurityConfig.class)
class BodyweightControllerSecurityTest {

    private static final String SUB = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WeighInService weighIns;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/kropp/vekt")).andExpect(status().isUnauthorized());
    }

    @Test
    void logRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/kropp/vekt").contentType(MediaType.APPLICATION_JSON).content("{\"weightKg\":80}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listWorksWhenAuthenticated() throws Exception {
        when(weighIns.listFor(any())).thenReturn(List.of());
        mvc.perform(get("/api/kropp/vekt").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
    }
}
