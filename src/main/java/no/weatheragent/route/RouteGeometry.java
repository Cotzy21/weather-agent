package no.weatheragent.route;

import java.util.ArrayList;
import java.util.List;

/**
 * Ren geometri for en rute: lengde, fortetting (sampling langs traséen) og
 * stigning fra en høydeprofil. Ingen nettverk - derfor lett å teste.
 */
public final class RouteGeometry {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private RouteGeometry() {
    }

    /** Samlet lengde i km langs waypoints (rett linje mellom hvert par). */
    public static double distanceKm(List<RoutePoint> points) {
        double sum = 0;
        for (int i = 1; i < points.size(); i++) {
            sum += haversineKm(points.get(i - 1), points.get(i));
        }
        return sum;
    }

    /**
     * Fordel punkter jevnt langs traséen, ca. hvert {@code stepKm}, men aldri
     * flere enn {@code maxPoints} (høyde-API-et tar maks 100 om gangen). Begge
     * endepunkter er med. Færre enn 2 waypoints returneres uendret.
     */
    public static List<RoutePoint> densify(List<RoutePoint> points, double stepKm, int maxPoints) {
        if (points.size() < 2) {
            return new ArrayList<>(points);
        }

        double total = distanceKm(points);
        if (total <= 0) {
            return new ArrayList<>(points);
        }

        int n = (int) Math.ceil(total / stepKm) + 1;
        n = Math.max(2, Math.min(maxPoints, n));

        // Kumulative avstander per waypoint.
        double[] cum = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cum[i] = cum[i - 1] + haversineKm(points.get(i - 1), points.get(i));
        }

        List<RoutePoint> samples = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double target = total * i / (n - 1);
            samples.add(pointAt(points, cum, target));
        }
        return samples;
    }

    /** Sum av positive høydeforskjeller langs profilen (samlet stigning i meter). */
    public static double ascentM(double[] elevations) {
        double ascent = 0;
        for (int i = 1; i < elevations.length; i++) {
            double diff = elevations[i] - elevations[i - 1];
            if (diff > 0) {
                ascent += diff;
            }
        }
        return ascent;
    }

    /** Interpoler et punkt som ligger {@code target} km ut langs traséen. */
    private static RoutePoint pointAt(List<RoutePoint> points, double[] cum, double target) {
        double total = cum[cum.length - 1];
        if (target <= 0) {
            return points.get(0);
        }
        if (target >= total) {
            return points.get(points.size() - 1);
        }
        int seg = 1;
        while (seg < cum.length && cum[seg] < target) {
            seg++;
        }
        double segLen = cum[seg] - cum[seg - 1];
        double f = segLen <= 0 ? 0 : (target - cum[seg - 1]) / segLen;
        RoutePoint a = points.get(seg - 1);
        RoutePoint b = points.get(seg);
        return new RoutePoint(a.lat() + (b.lat() - a.lat()) * f, a.lon() + (b.lon() - a.lon()) * f);
    }

    private static double haversineKm(RoutePoint a, RoutePoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(s));
    }
}
