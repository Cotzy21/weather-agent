package no.weatheragent.route;

import no.weatheragent.advice.CalorieAdvisor;
import no.weatheragent.advice.RouteAssessment;
import no.weatheragent.advice.RouteAssessor;
import no.weatheragent.advice.RouteEstimate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regner ut et estimat for en planlagt rute. Hvis ruting er aktivert
 * ({@link RoutingClient}) følger vi faktiske stier (foot-hiking) og bruker
 * lengde + stigning derfra. Ellers faller vi tilbake til rett linje mellom
 * waypoints + høydeprofil fra Open-Meteo. Tid/kalorier/snacks regnes av
 * {@link CalorieAdvisor}, og terrenget (vanskelighetsgrad/bratthet/utfordringer)
 * vurderes av {@link RouteAssessor} fra samme høydeprofil.
 */
@Component
public class RoutePlannerService {

    private static final double STEP_KM = 0.5;   // sample ca. hvert 500. meter
    private static final int MAX_SAMPLES = 100;  // høyde-API-et tar maks 100 punkter
    private static final int MAX_WAYPOINTS = 50; // sikkerhet (klient-input) + ORS-grense

    private final ElevationClient elevationClient;
    private final RoutingClient routingClient;

    public RoutePlannerService(ElevationClient elevationClient, RoutingClient routingClient) {
        this.elevationClient = elevationClient;
        this.routingClient = routingClient;
    }

    public RoutePlan plan(RouteRequest request) {
        List<RoutePoint> waypoints = limit(request.waypoints());
        double weightKg = request.weightKg();

        if (routingClient.isEnabled() && waypoints.size() >= 2) {
            try {
                RoutedPath path = routingClient.route(waypoints);
                RouteEstimate estimate = CalorieAdvisor.estimate(path.distanceKm(), path.ascentM(), weightKg);
                // Terrengvurderingen trenger profilen (ruteren gir bare summen).
                RouteAssessment assessment = assess(path.distanceKm(), profile(path.geometry()));
                return new RoutePlan(estimate, path.geometry(), true, assessment);
            } catch (Exception e) {
                // Ruting feilet (mangler nøkkel-tilgang, ingen rute funnet, e.l.) -> rett linje.
            }
        }

        double distance = RouteGeometry.distanceKm(waypoints);
        double[] profile = profile(waypoints);
        double ascent = profile == null ? 0 : RouteGeometry.ascentM(profile);
        RouteEstimate estimate = CalorieAdvisor.estimate(distance, ascent, weightKg);
        return new RoutePlan(estimate, waypoints, false, assess(distance, profile));
    }

    /** Begrens antall punkter fra klienten (unngå misbruk / for store kall). */
    private static List<RoutePoint> limit(List<RoutePoint> waypoints) {
        if (waypoints == null) {
            return List.of();
        }
        return waypoints.size() <= MAX_WAYPOINTS ? waypoints : waypoints.subList(0, MAX_WAYPOINTS);
    }

    /** Høydeprofil (jevnt fordelte punkter) langs traséen, eller null ved feil. */
    private double[] profile(List<RoutePoint> points) {
        if (points.size() < 2) {
            return null;
        }
        try {
            List<RoutePoint> samples = RouteGeometry.densify(points, STEP_KM, MAX_SAMPLES);
            return elevationClient.elevations(samples);
        } catch (Exception e) {
            return null; // vurdering/stigning droppes heller enn å velte hele planen
        }
    }

    private static RouteAssessment assess(double distanceKm, double[] profile) {
        return profile == null ? null : RouteAssessor.assess(distanceKm, profile);
    }
}
