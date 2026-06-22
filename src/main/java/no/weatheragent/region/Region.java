package no.weatheragent.region;

import no.weatheragent.geo.Location;

import java.util.List;

/**
 * En navngitt region og de stedene vi sammenligner vaeret mellom.
 * Stedene kommer fra den kuraterte lista i resources/regions.json.
 */
public record Region(String name, List<Location> places) {
}
