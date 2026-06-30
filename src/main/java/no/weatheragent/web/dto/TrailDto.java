package no.weatheragent.web.dto;

import no.weatheragent.hiking.Trail;

/** En turrute slik API-et eksponerer den. */
public record TrailDto(String name, String operator, double lat, double lon) {

    public static TrailDto from(Trail t) {
        return new TrailDto(t.name(), t.operator(), t.latitude(), t.longitude());
    }
}
