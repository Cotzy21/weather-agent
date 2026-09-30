package no.weatheragent.web.dto;

import no.weatheragent.body.WeighIn;

import java.time.LocalDate;
import java.util.UUID;

public record WeighInDto(UUID id, LocalDate date, double weightKg) {

    public static WeighInDto from(WeighIn w) {
        return new WeighInDto(w.getId(), w.getDate(), w.getWeightKg());
    }
}
