package no.weatheragent.training;

/**
 * Én melding i samtalen med treningsassistenten.
 *
 * @param role    "user" (brukeren) eller "assistant" (assistenten)
 * @param content selve teksten
 */
public record ChatTurn(String role, String content) {
}
