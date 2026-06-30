package no.weatheragent.web.dto;

import no.weatheragent.training.ProgressRow;

import java.time.LocalDate;

/** Ett punkt i progresjonen for en øvelse: beste vekt og volum en gitt dag. */
public record ProgressPointDto(LocalDate date, double maxWeight, double volume) {

    public static ProgressPointDto from(ProgressRow r) {
        return new ProgressPointDto(
                r.getDate(),
                r.getMaxWeight() == null ? 0 : r.getMaxWeight(),
                r.getVolume() == null ? 0 : r.getVolume());
    }
}
