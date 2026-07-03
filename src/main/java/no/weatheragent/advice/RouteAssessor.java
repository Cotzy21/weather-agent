package no.weatheragent.advice;

import no.weatheragent.advice.RouteAssessment.Difficulty;

import java.util.ArrayList;
import java.util.List;

/**
 * Vurderer terrenget på en planlagt rute ut fra distanse og høydeprofil.
 * Ren og testbar - ingen nettverk; profilen kommer fra høyde-API-et som
 * ruteplanleggeren allerede bruker.
 *
 * Graderingen er inspirert av DNTs merkestandard (forenklet): ruta får en
 * karakter for distanse, en for samlet stigning og en for bratteste parti,
 * og VERSTE karakter vinner - en kort, men stupbratt tur er ikke «grønn».
 *
 * Terskler (omtrentlige, kan finjusteres mot ekte DNT-verdier senere):
 *   distanse:  ≤5 km grønn · ≤10 blå · ≤20 rød · ellers svart
 *   stigning:  ≤300 m grønn · ≤600 blå · ≤1000 rød · ellers svart
 *   bratthet:  ≤10 % grønn · ≤15 % blå · ≤25 % rød · ellers svart
 */
public final class RouteAssessor {

    // «Vær forberedt på»-terskler.
    private static final double STEEP_WARN_PCT = 15;
    private static final double MOUNTAIN_WEATHER_M = 1000;
    private static final double LONG_TRIP_KM = 15;
    private static final double BIG_ASCENT_M = 800;
    private static final double BIG_DESCENT_M = 800;

    private RouteAssessor() {
    }

    /**
     * @param distanceKm rutas lengde
     * @param elevations høydeprofil med JEVNT fordelte punkter langs ruta
     *                   (slik {@code RouteGeometry.densify} + høyde-API-et gir)
     */
    public static RouteAssessment assess(double distanceKm, double[] elevations) {
        double ascent = 0;
        double descent = 0;
        double highest = elevations.length > 0 ? elevations[0] : 0;
        for (int i = 1; i < elevations.length; i++) {
            double diff = elevations[i] - elevations[i - 1];
            if (diff > 0) {
                ascent += diff;
            } else {
                descent -= diff;
            }
            highest = Math.max(highest, elevations[i]);
        }

        double maxGradient = maxGradientPct(distanceKm, elevations);
        Difficulty difficulty = worst(
                gradeDistance(distanceKm),
                gradeAscent(ascent),
                gradeGradient(maxGradient));

        return new RouteAssessment(difficulty, highest, maxGradient, descent,
                challenges(distanceKm, ascent, descent, highest, maxGradient));
    }

    /** Bratteste segment i prosent: høydeendring delt på segmentlengde. */
    private static double maxGradientPct(double distanceKm, double[] elevations) {
        if (elevations.length < 2 || distanceKm <= 0) {
            return 0;
        }
        double segmentM = distanceKm * 1000 / (elevations.length - 1);
        double max = 0;
        for (int i = 1; i < elevations.length; i++) {
            max = Math.max(max, Math.abs(elevations[i] - elevations[i - 1]) / segmentM * 100);
        }
        return max;
    }

    private static Difficulty gradeDistance(double km) {
        if (km <= 5) return Difficulty.GRONN;
        if (km <= 10) return Difficulty.BLAA;
        if (km <= 20) return Difficulty.ROED;
        return Difficulty.SVART;
    }

    private static Difficulty gradeAscent(double ascentM) {
        if (ascentM <= 300) return Difficulty.GRONN;
        if (ascentM <= 600) return Difficulty.BLAA;
        if (ascentM <= 1000) return Difficulty.ROED;
        return Difficulty.SVART;
    }

    private static Difficulty gradeGradient(double pct) {
        if (pct <= 10) return Difficulty.GRONN;
        if (pct <= 15) return Difficulty.BLAA;
        if (pct <= 25) return Difficulty.ROED;
        return Difficulty.SVART;
    }

    private static Difficulty worst(Difficulty... grades) {
        Difficulty worst = Difficulty.GRONN;
        for (Difficulty g : grades) {
            if (g.ordinal() > worst.ordinal()) {
                worst = g;
            }
        }
        return worst;
    }

    private static List<String> challenges(double km, double ascent, double descent,
                                           double highest, double maxGradient) {
        List<String> out = new ArrayList<>();
        if (maxGradient > STEEP_WARN_PCT) {
            out.add(String.format("Bratte partier (opptil ~%.0f %% helning) – gode sko, og bruk gjerne hendene der det trengs.", maxGradient));
        }
        if (highest > MOUNTAIN_WEATHER_M) {
            out.add(String.format("Ruta går opp til ~%.0f moh – fjellvær kan slå raskt om, ta med vindtett og varmt lag uansett varsel.", highest));
        }
        if (km > LONG_TRIP_KM) {
            out.add(String.format("Lang tur (%.0f km) – start tidlig og beregn god tid.", km));
        }
        if (ascent > BIG_ASCENT_M) {
            out.add(String.format("Mye stigning (~%.0f høydemeter) – fordel kreftene og ta pauser.", ascent));
        }
        if (descent > BIG_DESCENT_M) {
            out.add(String.format("Mye nedstigning (~%.0f m) – tungt for knærne, staver hjelper.", descent));
        }
        return out;
    }
}
