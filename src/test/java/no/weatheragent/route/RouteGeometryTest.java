package no.weatheragent.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteGeometryTest {

    @Test
    void distanceIsRoughlyCorrect() {
        // ~1 breddegrad nord ≈ 111 km.
        double d = RouteGeometry.distanceKm(List.of(new RoutePoint(60, 10), new RoutePoint(61, 10)));
        assertTrue(d > 110 && d < 112, "fikk " + d);
    }

    @Test
    void distanceOfSinglePointIsZero() {
        assertEquals(0.0, RouteGeometry.distanceKm(List.of(new RoutePoint(60, 10))));
    }

    @Test
    void densifyRespectsMaxAndKeepsEndpoints() {
        List<RoutePoint> wps = List.of(new RoutePoint(60, 10), new RoutePoint(61, 10));
        List<RoutePoint> dense = RouteGeometry.densify(wps, 0.5, 50); // ~111 km / 0.5 -> mange, kappes til 50

        assertEquals(50, dense.size());
        assertEquals(wps.get(0), dense.get(0));
        assertEquals(wps.get(wps.size() - 1), dense.get(dense.size() - 1));
    }

    @Test
    void densifyMidpointIsHalfway() {
        // ~222 km / 50 km -> mange punkter, kappes til 3 -> jevnt fordelt
        List<RoutePoint> dense = RouteGeometry.densify(
                List.of(new RoutePoint(60, 10), new RoutePoint(62, 10)), 50, 3);
        assertEquals(3, dense.size());
        assertEquals(61.0, dense.get(1).lat(), 0.01); // midtpunktet
    }

    @Test
    void ascentSumsOnlyPositiveDiffs() {
        // 100 -> 150 (+50) -> 120 (-) -> 170 (+50) = 100 m stigning
        double ascent = RouteGeometry.ascentM(new double[]{100, 150, 120, 170});
        assertEquals(100.0, ascent);
    }

    @Test
    void ascentIsZeroForFlatOrDownhill() {
        assertEquals(0.0, RouteGeometry.ascentM(new double[]{200, 180, 180, 150}));
    }
}
