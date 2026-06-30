package no.weatheragent.web.dto;

import no.weatheragent.advice.RouteEstimate;
import no.weatheragent.route.RoutePlan;

import java.util.List;

/**
 * Svaret fra ruteplanleggeren (/api/rute) slik API-et eksponerer det: estimatet
 * flatet ut til toppnivå, pluss traséen og om den fulgte faktiske stier.
 */
public record RoutePlanDto(double distanceKm,
                           double ascentM,
                           double hours,
                           int calories,
                           List<String> snacks,
                           List<PointDto> geometry,
                           boolean snappedToTrails) {

    public static RoutePlanDto from(RoutePlan plan) {
        RouteEstimate e = plan.estimate();
        return new RoutePlanDto(
                e.distanceKm(),
                e.ascentM(),
                e.hours(),
                e.calories(),
                e.snacks(),
                plan.geometry().stream().map(PointDto::from).toList(),
                plan.snappedToTrails());
    }
}
