package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Opprett en vane. Tom unit = ja/nei-vane. */
public record CreateHabitRequest(
        @NotBlank @Size(max = 40) String name,
        @Size(max = 16) String emoji,
        @Size(max = 20) String unit
) {
}
