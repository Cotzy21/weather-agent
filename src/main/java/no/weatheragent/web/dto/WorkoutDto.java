package no.weatheragent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.training.Workout;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** En logget treningsøkt slik API-et eksponerer den. */
public record WorkoutDto(UUID id,
                         LocalDate date,
                         String title,
                         String type,
                         JsonNode content,
                         String notes,
                         Instant createdAt) {

    public static WorkoutDto from(Workout w) {
        return new WorkoutDto(
                w.getId(), w.getDate(), w.getTitle(), w.getType(),
                w.getContent(), w.getNotes(), w.getCreatedAt());
    }
}
