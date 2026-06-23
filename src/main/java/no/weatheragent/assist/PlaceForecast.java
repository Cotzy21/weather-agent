package no.weatheragent.assist;

import no.weatheragent.hiking.Trail;
import no.weatheragent.ranking.DayWeather;

import java.util.List;

/**
 * Alt detaljsiden for ett sted trenger: flerdagers værvarsel (ett sammendrag
 * per dag), merkede turer i nærheten, og klær/utstyr-råd. Brukes av
 * GET /api/sted.
 */
public record PlaceForecast(String name,
                            double elevationMeters,
                            List<DayWeather> days,
                            List<Trail> trails,
                            List<String> clothing) {
}
