package no.weatheragent.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** One acceptance of an AI plan. Reuse clientId when retrying the same request. */
public record SavePlanBatchRequest(
        @NotNull UUID clientId,
        @NotNull @Size(min = 1, max = 7) List<@NotNull @Valid SavePlanRequest> workouts
) {}
