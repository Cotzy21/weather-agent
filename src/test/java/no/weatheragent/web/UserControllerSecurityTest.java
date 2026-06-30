package no.weatheragent.web;

import no.weatheragent.routes.SavedRouteService;
import no.weatheragent.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifiserer sikkerhetsreglene uten DB eller ekte Supabase: en mock-JwtDecoder
 * tilfredsstiller resource-server-konfigurasjonen, og jwt()-postprocessoren
 * simulerer en innlogget bruker.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SavedRouteService routes;

    @MockitoBean
    private JwtDecoder jwtDecoder; // kreves av oauth2-resource-server-konfigurasjonen

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/ruter")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsUserFromToken() throws Exception {
        String sub = "11111111-1111-1111-1111-111111111111";
        mvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject(sub).claim("email", "a@b.no"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sub))
                .andExpect(jsonPath("$.email").value("a@b.no"));
    }
}
