package no.weatheragent.web.dto;

import no.weatheragent.training.ProgressPoint;

import java.time.LocalDate;

/** Ett punkt i progresjonen for en øvelse: beste vekt og volum en gitt dag. */
public record ProgressPointDto(LocalDate date, double maxWeight, double volume) {

    public static ProgressPointDto from(ProgressPoint p) {
        return new ProgressPointDto(p.date(), p.maxWeight(), p.volume());
    }
}
