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
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void blocksImportButAllowsTheAuthenticatedSubjectToReadAndDelete() throws Exception {
        UUID user = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Oslo"));
        var signedIn = jwt().jwt(j -> j.subject(user.toString()));
        when(store.list(user, today.minusDays(13), today)).thenReturn(List.of());

        mvc.perform(post(PATH).with(signedIn).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"test-key\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reader, store);

        mvc.perform(get(PATH).with(signedIn).param("days", "14"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(delete(PATH).with(signedIn)).andExpect(status().isNoContent());
        verify(store).list(user, today.minusDays(13), today);
        verify(store).delete(user);
    }
}
