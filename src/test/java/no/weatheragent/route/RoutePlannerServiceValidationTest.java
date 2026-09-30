package no.weatheragent.route;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutePlannerServiceValidationTest {

    private static RouteRequest req(double kg, RoutePoint... points) {
        return new RouteRequest(List.of(points), kg);
    }

    @Test
    void acceptsAnOrdinaryRoute() {
        assertDoesNotThrow(() -> RoutePlannerService.validate(req(75, new RoutePoint(62.1, 6.8), new RoutePoint(62.2, 6.9))));
    }

    @Test
    void rejectsMissingOrEmptyWaypoints() {
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(null));
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(new RouteRequest(null, 75)));
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(new RouteRequest(List.of(), 75)));
    }

    @Test
    void rejectsTooManyWaypoints() {
        List<RoutePoint> many = IntStream.range(0, 51).mapToObj(i -> new RoutePoint(60, 6)).toList();
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(new RouteRequest(many, 75)));
    }

    @Test
    void rejectsOutOfRangeAndNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(req(75, new RoutePoint(95, 6))));
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(req(75, new RoutePoint(60, 181))));
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(req(75, new RoutePoint(Double.NaN, 6))));
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(
                new RouteRequest(Collections.singletonList(null), 75)));
    }

    @Test
    void rejectsImplausibleWeights() {
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(req(0, new RoutePoint(60, 6))));
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(req(1e9, new RoutePoint(60, 6))));
        assertThrows(IllegalArgumentException.class, () -> RoutePlannerService.validate(req(Double.NaN, new RoutePoint(60, 6))));
    }
}
