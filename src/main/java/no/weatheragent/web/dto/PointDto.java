package no.weatheragent.web.dto;

import no.weatheragent.route.RoutePoint;

/** Et punkt langs en rute slik API-et eksponerer det. */
public record PointDto(double lat, double lon) {

    public static PointDto from(RoutePoint p) {
        return new PointDto(p.lat(), p.lon());
    }
}
