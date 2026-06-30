package no.weatheragent.route;

import java.util.List;

/** Resultatet av å rute langs faktiske stier: lengde, stigning og selve traséen. */
public record RoutedPath(double distanceKm, double ascentM, List<RoutePoint> geometry) {
}
