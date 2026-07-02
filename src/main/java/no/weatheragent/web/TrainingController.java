package no.weatheragent.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.validation.Valid;
import no.weatheragent.training.Suggestion;
import no.weatheragent.training.TrainingPlanService;
import no.weatheragent.training.WorkoutService;
import no.weatheragent.training.WorkoutSuggester;
import no.weatheragent.web.dto.LogWorkoutRequest;
import no.weatheragent.web.dto.ProgressPointDto;
import no.weatheragent.web.dto.SavePlanRequest;
import no.weatheragent.web.dto.SuggestionDto;
import no.weatheragent.web.dto.SuggestionRequest;
import no.weatheragent.web.dto.TrainingPlanDto;
import no.weatheragent.web.dto.WorkoutDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Treningsassistenten: logg økter (ulike typer + fleksibelt JSONB-innhold), se egne
 * økter, og se progresjon per øvelse over tid. Alt scopes til innlogget bruker.
 */
@RestController
public class TrainingController {

    private final WorkoutService workouts;
    private final WorkoutSuggester suggester;
    private final TrainingPlanService plans;

    public TrainingController(WorkoutService workouts, WorkoutSuggester suggester, TrainingPlanService plans) {
        this.workouts = workouts;
        this.suggester = suggester;
        this.plans = plans;
    }

    @PostMapping("/api/treningsokter")
    public WorkoutDto log(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LogWorkoutRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        JsonNode content = request.content() == null ? JsonNodeFactory.instance.objectNode() : request.content();
        return WorkoutDto.from(
                workouts.log(userId, request.date(), request.title(), request.type(), content, request.notes()));
    }

    @GetMapping("/api/treningsokter")
    public List<WorkoutDto> mine(@AuthenticationPrincipal Jwt jwt) {
        return workouts.listFor(UUID.fromString(jwt.getSubject())).stream()
                .map(WorkoutDto::from)
                .toList();
    }

    @DeleteMapping("/api/treningsokter/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        boolean deleted = workouts.delete(id, UUID.fromString(jwt.getSubject()));
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/api/ovelser/progresjon")
    public List<ProgressPointDto> progression(@AuthenticationPrincipal Jwt jwt, @RequestParam("navn") String navn) {
        return workouts.progression(UUID.fromString(jwt.getSubject()), navn).stream()
                .map(ProgressPointDto::from)
                .toList();
    }

    /** AI-forslag til en økt ut fra fokus + brukerens historikk. */
    @PostMapping("/api/trening/forslag")
    public SuggestionDto suggest(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SuggestionRequest request) {
        Suggestion s = suggester.suggest(UUID.fromString(jwt.getSubject()), request.focus(), request.type());
        return SuggestionDto.from(s);
    }

    /** Lagre en plan i profilen - typisk et forslag brukeren likte. */
    @PostMapping("/api/trening/planer")
    public TrainingPlanDto savePlan(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SavePlanRequest request) {
        JsonNode content = request.content() == null ? JsonNodeFactory.instance.objectNode() : request.content();
        return TrainingPlanDto.from(plans.save(
                UUID.fromString(jwt.getSubject()),
                request.title(), request.type(), content, request.rationale()));
    }

    @GetMapping("/api/trening/planer")
    public List<TrainingPlanDto> myPlans(@AuthenticationPrincipal Jwt jwt) {
        return plans.listFor(UUID.fromString(jwt.getSubject())).stream()
                .map(TrainingPlanDto::from)
                .toList();
    }

    @DeleteMapping("/api/trening/planer/{id}")
    public ResponseEntity<Void> deletePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        boolean deleted = plans.delete(id, UUID.fromString(jwt.getSubject()));
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
