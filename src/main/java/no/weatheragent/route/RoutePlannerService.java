package no.weatheragent.route;

import no.weatheragent.advice.CalorieAdvisor;
import no.weatheragent.advice.RouteEstimate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regner ut et estimat for en planlagt rute: lengde fra waypoints, stigning fra
 * en samplet høydeprofil (Open-Meteo), og deretter tid/kalorier/snacks via
 * {@link CalorieAdvisor}.
 */
@Component
public class RoutePlannerService {

    private static final double STEP_KM = 0.5;   // sample ca. hvert 500. meter
    private static final int MAX_SAMPLES = 100;  // høyde-API-et tar maks 100 punkter

    private final ElevationClient elevationClient;

    public RoutePlannerService(ElevationClient elevationClient) {
        this.elevationClient = elevationClient;
    }

    public RouteEstimate plan(RouteRequest request) {
        List<RoutePoint> waypoints = request.waypoints() == null ? List.of() : request.waypoints();
        double distance = RouteGeometry.distanceKm(waypoints);
        double ascent = ascentFor(waypoints);
        return CalorieAdvisor.estimate(distance, ascent, request.weightKg());
    }

    /** Stigning fra høydeprofilen. Faller tilbake til 0 hvis høyde-API-et svikter. */
    private double ascentFor(List<RoutePoint> waypoints) {
        if (waypoints.size() < 2) {
            return 0;
        }
        try {
            List<RoutePoint> samples = RouteGeometry.densify(waypoints, STEP_KM, MAX_SAMPLES);
            return RouteGeometry.ascentM(elevationClient.elevations(samples));
        } catch (Exception e) {
            return 0; // heller et estimat uten stigning enn ingen svar
        }
    }
}
