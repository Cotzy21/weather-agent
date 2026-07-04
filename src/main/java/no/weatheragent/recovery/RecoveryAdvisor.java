package no.weatheragent.recovery;

import no.weatheragent.training.Workout;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Restitusjonsråd basert på de siste øktene - ren og testbar, alt innhold
 * hardkodet (generelle, veletablerte råd; IKKE medisinske råd, og det sier
 * frontenden også).
 *
 * Logikken er bevisst enkel: øktene fra i dag og i går bestemmer hvilke råd
 * som vises (en lang løpetur trenger annen restitusjon enn en styrkeøkt),
 * og mange treningsdager på rad utløser et hviledag-forslag.
 */
public final class RecoveryAdvisor {

    /** Fra så mange treningsdager på rad foreslår vi hviledag. */
    static final int REST_DAY_STREAK = 3;

    private static final Map<String, List<String>> STEPS_BY_TYPE = Map.of(
            "STYRKE", List.of(
                    "Protein (20-40 g) innen et par timer etter økta",
                    "7-9 timer søvn - det er der muskelen faktisk bygges",
                    "Lett aktivitet i morgen (gange eller rolig sykling) øker blodgjennomstrømmingen",
                    "Tøy lett - de trente muskelgruppene, uten å presse"),
            "LØPING", List.of(
                    "Karbohydrater og væske den første timen etter løpeturen",
                    "Rolig gange eller lett sykling dagen etter (aktiv restitusjon)",
                    "Tøy eller foam-rull legger, hofteleddsbøyere og bakside lår",
                    "Såre legger flere dager på rad? Ta en ekstra hviledag før neste løpetur"),
            "SYKKEL", List.of(
                    "Karbohydrater og væske rett etter lange turer",
                    "Tøy hofteleddsbøyere og framside lår - de jobber mest på sykkelen",
                    "Lett spinning dagen etter løsner opp",
                    "Knesmerter? Sjekk setehøyden - feil innstilling er den vanligste synderen"),
            "SVØMMING", List.of(
                    "Etterfyll væske - man svetter mer i vann enn man merker",
                    "Tøy skuldre og bryst lett",
                    "Skulderplager? Varier svømmetakene og kutt padleutstyr en periode"),
            "HIKING", List.of(
                    "Etterfyll væske og salter etter lange turer",
                    "Beina høyt en stund i kveld - reduserer hevelse",
                    "Tøy legger, lår og hofter",
                    "Ømme knær etter nedstigning er normalt - gi dem en rolig dag"),
            "BULDRING", List.of(
                    "Tøy underarmer og fingre LETT - aldri hardt rett etter klatring",
                    "Gi fingrene ~48 timer før neste harde buldreøkt - sener tilpasser seg saktere enn muskler",
                    "Ømme fingerledd? Kontrastbad (vekselvis varmt og kaldt vann) kan lindre"),
            "FRISTIL", List.of(
                    "Væske og et balansert måltid etter økta",
                    "7-9 timer søvn",
                    "Lett bevegelse i morgen er bedre enn full ro"));

    private RecoveryAdvisor() {
    }

    /** Råd for øktene fra i dag og i går, i rekkefølgen de ble logget (nyest først). */
    public static List<RecoveryAdvice> advise(List<Workout> workouts, LocalDate today) {
        List<RecoveryAdvice> advice = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Workout w : workouts) {
            boolean isToday = today.equals(w.getDate());
            boolean isYesterday = today.minusDays(1).equals(w.getDate());
            if (!isToday && !isYesterday) {
                continue;
            }
            String type = w.getType() == null ? "" : w.getType().toUpperCase(Locale.ROOT);
            if (!seen.add(type)) {
                continue; // ett rådskort per type holder
            }
            List<String> steps = STEPS_BY_TYPE.getOrDefault(type, STEPS_BY_TYPE.get("FRISTIL"));
            advice.add(new RecoveryAdvice(type, isToday ? "i dag" : "i går", steps));
        }
        return advice;
    }

    /**
     * Antall treningsdager PÅ RAD, telt bakover fra i dag (eller i går, hvis
     * i dag er treningsfri så langt - kveldsøkta kan jo komme ennå).
     */
    public static int trainingStreak(List<Workout> workouts, LocalDate today) {
        Set<LocalDate> trainingDays = new LinkedHashSet<>();
        for (Workout w : workouts) {
            trainingDays.add(w.getDate());
        }

        LocalDate day = trainingDays.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (trainingDays.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }
}
