package no.weatheragent.web;

import jakarta.validation.Valid;
import no.weatheragent.habit.HabitService;
import no.weatheragent.web.dto.CreateHabitRequest;
import no.weatheragent.web.dto.HabitDto;
import no.weatheragent.web.dto.LogHabitRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Habit tracker over REST: vaner med daglig logg og streak. Alt scopes til
 * innlogget bruker, samme mønster som resten av API-et.
 */
@RestController
public class HabitController {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @GetMapping("/api/vaner")
    public List<HabitDto> mine(@AuthenticationPrincipal Jwt jwt) {
        return habitService.listFor(UUID.fromString(jwt.getSubject()), LocalDate.now(OSLO)).stream()
                .map(HabitDto::from)
                .toList();
    }

    @PostMapping("/api/vaner")
    public HabitDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateHabitRequest request) {
        var habit = habitService.create(UUID.fromString(jwt.getSubject()),
                request.name(), request.emoji(), request.unit());
        return new HabitDto(habit.getId(), habit.getName(), habit.getEmoji(), habit.getUnit(), 0, List.of());
    }

    @DeleteMapping("/api/vaner/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        boolean deleted = habitService.delete(id, UUID.fromString(jwt.getSubject()));
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Sett dagens verdi (upsert; verdi <= 0 fjerner loggen for dagen). */
    @PutMapping("/api/vaner/{id}/logg")
    public ResponseEntity<Void> log(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody LogHabitRequest request) {
        habitService.log(id, UUID.fromString(jwt.getSubject()), request.date(), request.value());
        return ResponseEntity.noContent().build();
    }
}
