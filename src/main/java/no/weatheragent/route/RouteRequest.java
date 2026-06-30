package no.weatheragent.route;

import java.util.List;

/** Forespørsel fra ruteplanleggeren: klikkede waypoints + brukerens vekt. */
public record RouteRequest(List<RoutePoint> waypoints, double weightKg) {
}
