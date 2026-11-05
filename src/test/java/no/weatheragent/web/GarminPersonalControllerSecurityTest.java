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
import org.springframework.web.client.HttpClientErrorException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GarminPersonalController.class)
@Import(SecurityConfig.class)
class GarminPersonalControllerSecurityTest {
    private static final String PATH = "/api/trening/garmin/aktiviteter";

    @Autowired MockMvc mvc;
    @MockitoBean GarminPersonalClient garmin;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void requiresLoginAndRejectsInvalidInputBeforeContactingGarmin() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"test-key\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"bad key\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(garmin);
    }

    @Test
    void providerErrorDoesNotExposeCredentials() throws Exception {
        when(garmin.recentActivities("test-key"))
                .thenThrow(new HttpClientErrorException(UNAUTHORIZED, "test-key"));
        mvc.perform(post(PATH).with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"test-key\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(not(containsString("test-key"))));
    }
}
