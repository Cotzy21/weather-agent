package no.weatheragent.recovery;

import java.util.List;

/**
 * Restitusjonsråd etter en nylig økt: hvilken økt-type det gjelder, når den
 * var («i dag»/«i går»), og konkrete steg. Innholdet er hardkodet per type -
 * null API-kall.
 */
public record RecoveryAdvice(String type, String when, List<String> steps) {
}
