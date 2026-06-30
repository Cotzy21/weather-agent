package no.weatheragent.web;

import jakarta.validation.Valid;
import no.weatheragent.training.SetInput;
import no.weatheragent.training.WorkoutService;
import no.weatheragent.web.dto.LogWorkoutRequest;
import no.weatheragent.web.dto.ProgressPointDto;
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
 * Treningsassistentens første del: logg økter, se egne økter, og se progresjon
 * per øvelse over tid. Alt scopes til innlogget bruker (Supabase-JWT sub).
 */
@RestController
public class TrainingController {

    private final WorkoutService workouts;

    public TrainingController(WorkoutService workouts) {
        this.workouts = workouts;
    }

    @PostMapping("/api/treningsokter")
    public WorkoutDto log(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LogWorkoutRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        List<SetInput> sets = request.sets().stream()
                .map(s -> new SetInput(s.exercise(), s.reps(), s.weightKg()))
                .toList();
        return WorkoutDto.from(workouts.log(userId, request.date(), request.title(), sets));
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
}
