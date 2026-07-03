package no.weatheragent.web.dto;

import no.weatheragent.advice.RouteAssessment;
import no.weatheragent.advice.RouteEstimate;
import no.weatheragent.route.RoutePlan;

import java.util.List;

/**
 * Svaret fra ruteplanleggeren (/api/rute) slik API-et eksponerer det: estimatet
 * flatet ut til toppnivå, traséen, om den fulgte faktiske stier, og
 * terrengvurderingen (null når høydeprofilen ikke kunne hentes).
 */
public record RoutePlanDto(double distanceKm,
                           double ascentM,
                           double hours,
                           int calories,
                           List<String> snacks,
                           List<PointDto> geometry,
                           boolean snappedToTrails,
                           AssessmentDto assessment) {

    /** Terrengvurderingen for UI: gradering m/farge-nøkkel + fakta + utfordringer. */
    public record AssessmentDto(String difficulty,
                                String difficultyLabel,
                                double highestPointM,
                                double maxGradientPct,
                                List<String> challenges) {

        static AssessmentDto from(RouteAssessment a) {
            return a == null ? null : new AssessmentDto(
                    a.difficulty().name(),
                    a.difficulty().label(),
                    a.highestPointM(),
                    a.maxGradientPct(),
                    a.challenges());
        }
    }

    public static RoutePlanDto from(RoutePlan plan) {
        RouteEstimate e = plan.estimate();
        return new RoutePlanDto(
                e.distanceKm(),
                e.ascentM(),
                e.hours(),
                e.calories(),
                e.snacks(),
                plan.geometry().stream().map(PointDto::from).toList(),
                plan.snappedToTrails(),
                AssessmentDto.from(plan.assessment()));
    }
}
