package no.weatheragent.route;

import no.weatheragent.advice.RouteAssessment;
import no.weatheragent.advice.RouteEstimate;

import java.util.List;

/**
 * Svaret fra ruteplanleggeren: estimatet (tid/kalorier/snacks), selve traséen
 * (til å tegne på kart), om den fulgte faktiske stier eller bare rett linje,
 * og terrengvurderingen (null hvis høydeprofilen ikke kunne hentes).
 */
public record RoutePlan(RouteEstimate estimate, List<RoutePoint> geometry, boolean snappedToTrails,
                        RouteAssessment assessment) {
}
