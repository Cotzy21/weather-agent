package no.weatheragent.training;

import no.weatheragent.training.Readiness.Level;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Dagsform fra søvn - ren og testbar, null API-kall. Erstatter en «sleep score»
 * fra klokke til integrasjonene kommer; da kan HRV/søvnscore mates inn her.
 *
 * Regler (voksne trenger typisk 7-9 timer):
 *   GOD:     siste natt ≥ 7 t OG snitt siste tre netter ≥ 7 t
 *   LAV:     siste natt < 6 t ELLER snitt < 6 t
 *   MIDDELS: alt imellom
 * Søvnen som gjelder er den logget i dag (natta som var); mangler den, brukes
 * i går. Eldre enn det er ikke «dagsform» lenger.
 */
public final class ReadinessAdvisor {

    private static final double GOOD_HOURS = 7;
    private static final double LOW_HOURS = 6;

    private ReadinessAdvisor() {
    }

    public static Optional<Readiness> assess(Map<LocalDate, Double> sleepByDate, LocalDate today, boolean english) {
        LocalDate night = sleepByDate.containsKey(today) ? today
                : sleepByDate.containsKey(today.minusDays(1)) ? today.minusDays(1) : null;
        if (night == null) {
            return Optional.empty();
        }

        double last = sleepByDate.get(night);
        double sum = 0;
        int count = 0;
        for (int i = 0; i < 3; i++) {
            Double h = sleepByDate.get(night.minusDays(i));
            if (h != null) {
                sum += h;
                count++;
            }
        }
        double avg = sum / count;

        Level level = last < LOW_HOURS || avg < LOW_HOURS ? Level.LAV
                : last >= GOOD_HOURS && avg >= GOOD_HOURS ? Level.GOD
                : Level.MIDDELS;

        return Optional.of(new Readiness(level, night, last, avg, advice(level, english)));
    }

    private static String advice(Level level, boolean english) {
        return switch (level) {
            case GOD -> english
                    ? "Well rested – a good day for a hard session or a new personal best."
                    : "Godt uthvilt – fin dag for en hard økt eller en ny personlig rekord.";
            case MIDDELS -> english
                    ? "A bit short on sleep – train as planned, but skip the very heaviest sets if you feel sluggish."
                    : "Litt kort søvn – tren som planlagt, men dropp de aller tyngste settene hvis kroppen føles treg.";
            case LAV -> english
                    ? "Low on sleep – choose an easier session (lower intensity, ~20–30% less volume) or active rest. "
                    + "Heavy lifts and intervals can wait."
                    : "Lite søvn – velg en lettere økt (lavere intensitet, ~20–30 % mindre volum) eller aktiv hvile. "
                    + "Tunge løft og intervaller kan vente.";
        };
    }
}
