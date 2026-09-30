package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Øvelsesnavn fra en import som skal kobles til appens øvelser. */
public record MatchExercisesRequest(@NotEmpty @Size(max = 200) List<@NotBlank @Size(max = 80) String> names) {
}
