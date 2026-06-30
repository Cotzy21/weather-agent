package no.weatheragent.web.dto;

import no.weatheragent.geo.Location;
import no.weatheragent.ranking.RankedPlaceOverPeriod;

/**
 * Ett rangert sted slik API-et eksponerer det: ferdig utregnede snitt-tall, så
 * klienten slipper å regne dem ut selv. Dette er en DTO (Data Transfer Object) -
 * en bevisst, stabil form for utsiden, koblet fra de interne domene-recordene.
 */
public record PlaceDto(String name,
                       double lat,
                       double lon,
                       double elevationM,
                       double avgTempC,
                       double avgPrecipMm,
                       double avgWindMs,
                       double score) {

    public static PlaceDto from(RankedPlaceOverPeriod p) {
        Location l = p.location();
        return new PlaceDto(
                l.name(), l.latitude(), l.longitude(), p.elevationMeters(),
                p.avgMaxTempC(), p.avgPrecipMm(), p.avgWindMs(), p.score());
    }
}
