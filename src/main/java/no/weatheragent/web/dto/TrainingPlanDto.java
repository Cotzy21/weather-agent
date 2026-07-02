package no.weatheragent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import no.weatheragent.training.TrainingPlan;

import java.util.UUID;

/** En lagret treningsplan slik frontenden viser den (og fyller øktskjemaet med). */
public record TrainingPlanDto(
        UUID id,
        String title,
        String type,
        JsonNode content,
        String rationale
) {

    public static TrainingPlanDto from(TrainingPlan plan) {
        return new TrainingPlanDto(plan.getId(), plan.getTitle(), plan.getType(),
                plan.getContent(), plan.getRationale());
    }
}
