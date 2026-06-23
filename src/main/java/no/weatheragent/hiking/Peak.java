package no.weatheragent.hiking;

import no.weatheragent.geo.Location;

import java.util.Optional;

/**
 * En navngitt fjelltopp fra OpenStreetMap: et sted pluss (ofte) en høyde.
 * Høyden brukes til å velge ut de mest interessante kandidatene per område.
 *
 * @param location       navn + koordinat (gjenbruker {@link Location})
 * @param elevationMeters høyde over havet i meter, eller null hvis ukjent
 */
public record Peak(Location location, Double elevationMeters) {

    public Optional<Double> elevation() {
        return Optional.ofNullable(elevationMeters);
    }
}
