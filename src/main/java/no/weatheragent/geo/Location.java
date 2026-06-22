package no.weatheragent.geo;

/**
 * Et navngitt geografisk punkt. Brukes både av geocoding (sted -> koordinat)
 * og av vaerklienten (koordinat -> vaer).
 */
public record Location(String name, double latitude, double longitude) {
}
