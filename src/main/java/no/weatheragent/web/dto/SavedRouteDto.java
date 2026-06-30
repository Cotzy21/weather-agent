package no.weatheragent.web.dto;

import no.weatheragent.routes.SavedRoute;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** En lagret rute slik API-et eksponerer den. */
public record SavedRouteDto(UUID id,
                            String name,
                            double distanceKm,
                            double ascentM,
                            List<PointDto> geometry,
                            Instant createdAt) {

    public static SavedRouteDto from(SavedRoute r) {
        return new SavedRouteDto(
                r.getId(),
                r.getName(),
                r.getDistanceKm(),
                r.getAscentM(),
                r.getGeometry().stream().map(PointDto::from).toList(),
                r.getCreatedAt());
    }
}
