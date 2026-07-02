package no.weatheragent.web;

import no.weatheragent.nutrition.NutritionFavorite;
import no.weatheragent.nutrition.NutritionFavoriteService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sikkerhet + kontrakt for kosthold-API-et, uten DB (mock service, mock JwtDecoder).
 */
@WebMvcTest(NutritionController.class)
@Import(SecurityConfig.class)
class NutritionControllerSecurityTest {

    private static final String SUB = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NutritionFavoriteService favorites;

    @MockitoBean
    private JwtDecoder jwtDecoder; // kreves av oauth2-resource-server-konfigurasjonen

    @Test
    void favorittersRequireAuthentication() throws Exception {
        mvc.perform(get("/api/kosthold/favoritter")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/kosthold/favoritter")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/kosthold/favoritter").param("key", "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsOwnFavorites() throws Exception {
        when(favorites.listFor(UUID.fromString(SUB))).thenReturn(List.of(
                new NutritionFavorite(UUID.fromString(SUB), "gronnsaker:Brokkoli", "Brokkoli", "🥦", "vitaminer")));

        mvc.perform(get("/api/kosthold/favoritter").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("gronnsaker:Brokkoli"))
                .andExpect(jsonPath("$[0].name").value("Brokkoli"))
                .andExpect(jsonPath("$[0].tag").value("vitaminer"));
    }

    @Test
    void addValidatesAndReturnsFavorite() throws Exception {
        when(favorites.add(eq(UUID.fromString(SUB)), eq("frukt:Eple"), eq("Eple"), any(), any()))
                .thenReturn(new NutritionFavorite(UUID.fromString(SUB), "frukt:Eple", "Eple", "🍎", "vitaminer"));

        mvc.perform(post("/api/kosthold/favoritter")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"frukt:Eple\",\"name\":\"Eple\",\"emoji\":\"🍎\",\"tag\":\"vitaminer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("frukt:Eple"));
    }

    @Test
    void addRejectsBlankKey() throws Exception {
        mvc.perform(post("/api/kosthold/favoritter")
                        .with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"\",\"name\":\"Eple\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns404WhenNothingWasRemoved() throws Exception {
        when(favorites.remove(UUID.fromString(SUB), "frukt:Eple")).thenReturn(false);

        mvc.perform(delete("/api/kosthold/favoritter")
                        .param("key", "frukt:Eple")
                        .with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isNotFound());
    }
}
