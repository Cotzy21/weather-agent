package no.weatheragent.advice;

import java.util.List;

/**
 * Et grovt estimat for en planlagt tur: distanse, stigning, antatt tid,
 * kaloriforbruk og forslag til mat/drikke. Bevisst et estimat - faktisk forbruk
 * varierer med tempo, last, underlag og form.
 */
public record RouteEstimate(double distanceKm,
                            double ascentM,
                            double hours,
                            int calories,
                            List<String> snacks) {
}
