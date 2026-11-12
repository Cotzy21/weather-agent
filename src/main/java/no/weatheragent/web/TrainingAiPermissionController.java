package no.weatheragent.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.weatheragent.account.TrainingAiPermission;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/konto/trenings-ai")
public class TrainingAiPermissionController {
    private final TrainingAiPermission permission;

    public TrainingAiPermissionController(TrainingAiPermission permission) {
        this.permission = permission;
    }

    public record Request(@NotNull Integer version, @NotNull Boolean allowed) {}

    @GetMapping
    public TrainingAiPermission.Decision get(@AuthenticationPrincipal Jwt jwt) {
        return permission.get(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping
    public TrainingAiPermission.Decision update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Request request) {
        return permission.record(UUID.fromString(jwt.getSubject()), request.version(), request.allowed());
    }
}
