package no.weatheragent.training;

import java.time.LocalDate;
import java.util.List;

/**
 * Forslag til neste økt for én øvelse, fra {@link ProgressionAdvisor}.
 *
 * @param exercise      øvelsen, slik brukeren skrev den sist
 * @param lastDate      datoen øvelsen sist ble trent
 * @param lastWeightKg  toppvekta sist (0 = kroppsvekt)
 * @param lastReps      reps på arbeidssettene sist (settene med toppvekta)
 * @param action        hva motoren anbefaler
 * @param nextWeightKg  anbefalt vekt neste gang
 * @param nextReps      anbefalt reps per sett neste gang
 * @param sets          antall arbeidssett (samme som sist)
 * @param reason        kort begrunnelse på valgt språk
 */
public record NextSetSuggestion(
        String exercise,
        LocalDate lastDate,
        double lastWeightKg,
        List<Integer> lastReps,
        Action action,
        double nextWeightKg,
        int nextReps,
        int sets,
        String reason
) {

    public enum Action {
        /** Alle sett nådde toppen av rep-området -> legg på vekt. */
        OK_VEKT,
        /** Ikke alle sett nådde toppen ennå -> samme vekt, flere reps. */
        FLERE_REPS,
        /** Stått stille flere økter -> ta ned vekta og bygg opp igjen. */
        DELOAD,
        /** Kroppsvektøvelse -> flere reps. */
        KROPPSVEKT
    }
}
