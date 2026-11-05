package no.weatheragent.web;

import no.weatheragent.garmin.GarminInsightsService;
import no.weatheragent.garmin.GarminWellnessReader;
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
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GarminInsightsController.class)
@Import(SecurityConfig.class)
class GarminInsightsSecurityTest {
    private static final String PATH = "/api/recovery/garmin";
    @Autowired MockMvc mvc;
    @MockitoBean GarminWellnessReader reader;
    @MockitoBean GarminInsightsService store;
    @MockitoBean JwtDecoder decoder;

    @Test
    void blocksUnauthenticatedReadsWritesAndDeletes() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reader, store);
    }

    @Test
    void savesAndDeletesOnlyForTheAuthenticatedSubject() throws Exception {
        UUID user = UUID.randomUUID();
        LocalDate day = LocalDate.now(ZoneId.of("Europe/Oslo")).minusDays(2);
        var measurements = Map.of(day, Map.of("sleepMin", 420.0));
        when(reader.read("test-key", GarminWellnessReader.Group.WELLNESS, day, day)).thenReturn(measurements);
        when(store.save(user, measurements)).thenReturn(1);
        String request = """
                {"apiKey":"test-key","group":"WELLNESS","from":"%s","to":"%s","userId":"00000000-0000-0000-0000-000000000000"}
                """.formatted(day, day);
        mvc.perform(post(PATH).with(jwt().jwt(j -> j.subject(user.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.days").value(1));

        mvc.perform(delete(PATH).with(jwt().jwt(j -> j.subject(user.toString())))).andExpect(status().isNoContent());
        verify(store).save(user, measurements);
        verify(store).delete(user);
    }
}
