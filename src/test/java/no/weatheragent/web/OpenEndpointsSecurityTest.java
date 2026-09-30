package no.weatheragent.web;

import no.weatheragent.assist.TurvaerService;
import no.weatheragent.route.RoutePlannerService;
import no.weatheragent.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** De åpne endepunktene (uten innlogging) må avvise ugyldig input før de bruker LLM/kart/vær-tjenester. */
@WebMvcTest(TurvaerController.class)
@Import(SecurityConfig.class)
class OpenEndpointsSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TurvaerService service;

    @MockitoBean
    private RoutePlannerService routePlanner;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void weatherSearchRejectsEmptyAndHugeQueriesBeforeCallingTheLlm() throws Exception {
        mvc.perform(get("/api/turvaer").param("q", "  ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/turvaer").param("q", "x".repeat(301))).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void placeDetailRejectsInvalidCoordinatesAndNames() throws Exception {
        mvc.perform(get("/api/sted").param("name", "Slogen").param("lat", "91").param("lon", "6"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sted").param("name", "Slogen").param("lat", "NaN").param("lon", "6"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sted").param("name", "x".repeat(121)).param("lat", "62").param("lon", "6"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void placeDetailRoundsCoordinatesSoTheCacheCannotBeFilledWithNearlyIdenticalPoints() throws Exception {
        org.mockito.Mockito.when(service.placeDetail(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(new no.weatheragent.assist.PlaceForecast("Slogen", 0, java.util.List.of(), java.util.List.of(), java.util.List.of()));
        mvc.perform(get("/api/sted").param("name", "Slogen").param("lat", "62.1834567").param("lon", "6.8612345"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(service).placeDetail("Slogen", 62.183, 6.861);
    }

    @Test
    void responsesCarryContentSecurityPolicyAndOtherHardeningHeaders() throws Exception {
        mvc.perform(get("/api/turvaer").param("q", " "))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("object-src 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    void errorResponsesDoNotLeakInternals() throws Exception {
        mvc.perform(post("/api/rute").contentType("application/json").content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
