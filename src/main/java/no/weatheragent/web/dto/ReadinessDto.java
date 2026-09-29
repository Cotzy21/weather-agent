package no.weatheragent.web.dto;

import no.weatheragent.training.Readiness;

import java.time.LocalDate;

/** Dagsform fra søvn, slik /api/trening/dagsform leverer den. */
public record ReadinessDto(String level, LocalDate nightDate, double lastNightHours, double avg3Hours, String advice) {

    public static ReadinessDto from(Readiness r) {
        return new ReadinessDto(r.level().name(), r.nightDate(), r.lastNightHours(), r.avg3Hours(), r.advice());
    }
}
