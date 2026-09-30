package no.weatheragent.web;

import jakarta.validation.Valid;
import no.weatheragent.route.RouteStoryService;
import no.weatheragent.training.AiRateLimiter;
import no.weatheragent.web.dto.RouteStoryRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** «Fortell om turen»: LLM-omtale av en planlagt rute. Krever innlogging og teller mot AI-kvoten per bruker. */
@RestController
public class RouteStoryController {

    private final RouteStoryService stories;
    private final AiRateLimiter aiLimit;

    public RouteStoryController(RouteStoryService stories, AiRateLimiter aiLimit) {
        this.stories = stories;
        this.aiLimit = aiLimit;
    }

    @PostMapping("/api/ruter/fortelling")
    public RouteStoryRequest.Reply story(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RouteStoryRequest request) {
        aiLimit.check(UUID.fromString(jwt.getSubject()));
        return new RouteStoryRequest.Reply(stories.tell(request.toFacts(), request.lang()));
    }
}
