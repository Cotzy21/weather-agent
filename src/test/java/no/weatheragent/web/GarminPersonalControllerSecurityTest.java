package no.weatheragent.web;

import no.weatheragent.garmin.GarminPersonalClient;
import no.weatheragent.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GarminPersonalController.class)
@Import(SecurityConfig.class)
class GarminPersonalControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean GarminPersonalClient garmin;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void blocksGarminRequestsBeforeContactingTheProvider() throws Exception {
        for (String endpoint : new String[]{"aktiviteter", "filer"}) {
            String path = "/api/trening/garmin/" + endpoint;
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"test-key\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"apiKey\":\"test-key\",\"ids\":[1]}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(garmin);
    }
}
