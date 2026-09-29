package no.weatheragent.web.dto;

import no.weatheragent.hiking.Trail;

import java.util.List;

/**
 * En turrute slik API-et eksponerer den. {@code lines} er polylinjer som
 * [[lat, lon], …] (kompakt JSON) - tom når bare senterpunktet er kjent.
 */
public record TrailDto(String name, String operator, double lat, double lon, List<List<double[]>> lines) {

    public static TrailDto from(Trail t) {
        List<List<double[]>> lines = t.lines().stream()
                .map(line -> line.stream().map(p -> new double[]{p.lat(), p.lon()}).toList())
                .toList();
        return new TrailDto(t.name(), t.operator(), t.latitude(), t.longitude(), lines);
    }
}
