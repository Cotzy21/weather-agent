package no.weatheragent.web;

import no.weatheragent.route.RouteStoryService;
import no.weatheragent.security.SecurityConfig;
import no.weatheragent.training.AiRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteStoryController.class)
@Import(SecurityConfig.class)
class RouteStoryControllerSecurityTest {

    private static final String SUB = "44444444-4444-4444-4444-444444444444";
    private static final String BODY = """
            {"name":"Slogen","distanceKm":12.3,"ascentM":850,"hours":4.5,"calories":780,"difficulty":"Middels",
             "highestPointM":1120,"maxGradientPct":18,"challenges":["Bratt siste del"],"snacks":["Nøtter"],"lang":"nb"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RouteStoryService stories;

    @MockitoBean
    private AiRateLimiter aiLimit;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void requiresLoginBecauseItCostsAnLlmCall() throws Exception {
        mvc.perform(post("/api/ruter/fortelling").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(stories, aiLimit);
    }

    @Test
    void checksTheUsersAiQuotaBeforeCallingTheModelAndReturnsTheStory() throws Exception {
        when(stories.tell(any(), eq("nb"))).thenReturn("En fin og krevende tur.");

        mvc.perform(post("/api/ruter/fortelling").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.story").value("En fin og krevende tur."));

        var order = inOrder(aiLimit, stories);
        order.verify(aiLimit).check(UUID.fromString(SUB));
        order.verify(stories).tell(any(), eq("nb"));
    }

    @Test
    void whenTheQuotaIsUsedUpNoModelCallIsMadeAndTheAnswerIs429WithRetryAfter() throws Exception {
        doThrow(new AiRateLimiter.LimitExceededException("Du har brukt opp AI-kvoten.", 120))
                .when(aiLimit).check(UUID.fromString(SUB));

        mvc.perform(post("/api/ruter/fortelling").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType("application/json").content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"));
        verifyNoInteractions(stories);
    }

    @Test
    void rejectsAbsurdOrOversizedInputBeforeAnyQuotaOrModelUse() throws Exception {
        String[] bad = {
                BODY.replace("\"name\":\"Slogen\"", "\"name\":\"  \""),
                BODY.replace("\"name\":\"Slogen\"", "\"name\":\"" + "x".repeat(121) + "\""),
                BODY.replace("\"distanceKm\":12.3", "\"distanceKm\":5000"),
                BODY.replace("\"ascentM\":850", "\"ascentM\":-1"),
                BODY.replace("\"challenges\":[\"Bratt siste del\"]", "\"challenges\":[\"" + "y".repeat(201) + "\"]"),
                BODY.replace("\"lang\":\"nb\"", "\"lang\":\"xx\""),
                "{}",
        };
        for (String body : bad) {
            mvc.perform(post("/api/ruter/fortelling").with(jwt().jwt(j -> j.subject(SUB)))
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(stories, aiLimit);
    }
}
