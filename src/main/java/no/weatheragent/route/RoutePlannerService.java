package no.weatheragent.route;

import no.weatheragent.advice.CalorieAdvisor;
import no.weatheragent.advice.RouteEstimate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regner ut et estimat for en planlagt rute. Hvis ruting er aktivert
 * ({@link RoutingClient}) følger vi faktiske stier (foot-hiking) og bruker
 * lengde + stigning derfra. Ellers faller vi tilbake til rett linje mellom
 * waypoints + høydeprofil fra Open-Meteo. Til slutt regnes tid/kalorier/snacks
 * av {@link CalorieAdvisor}.
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
                return new RoutePlan(estimate, path.geometry(), true);
            } catch (Exception e) {
                // Ruting feilet (mangler nøkkel-tilgang, ingen rute funnet, e.l.) -> rett linje.
            }
        }

        double distance = RouteGeometry.distanceKm(waypoints);
        double ascent = straightLineAscent(waypoints);
        RouteEstimate estimate = CalorieAdvisor.estimate(distance, ascent, weightKg);
        return new RoutePlan(estimate, waypoints, false);
    }

    /** Begrens antall punkter fra klienten (unngå misbruk / for store kall). */
    private static List<RoutePoint> limit(List<RoutePoint> waypoints) {
        if (waypoints == null) {
            return List.of();
        }
        return waypoints.size() <= MAX_WAYPOINTS ? waypoints : waypoints.subList(0, MAX_WAYPOINTS);
    }

    /** Stigning fra Open-Meteo-høydeprofil. Faller tilbake til 0 hvis API-et svikter. */
    private double straightLineAscent(List<RoutePoint> waypoints) {
        if (waypoints.size() < 2) {
            return 0;
        }
        try {
            List<RoutePoint> samples = RouteGeometry.densify(waypoints, STEP_KM, MAX_SAMPLES);
            return RouteGeometry.ascentM(elevationClient.elevations(samples));
        } catch (Exception e) {
            return 0;
        }
    }
}
