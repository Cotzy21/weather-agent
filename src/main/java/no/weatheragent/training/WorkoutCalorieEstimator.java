package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Grovt kaloriestimat for en logget økt - ren og testbar, ingen nettverk.
 * Brukes av kostholdsdagboka for å vise «trening: -X kcal» i dagsbalansen.
 *
 * Modell (MET-tabell, samme tilnærming som {@link no.weatheragent.advice.CalorieAdvisor}):
 *  - kcal = MET · kg · timer. Varighet fra økta; styrkeøkter uten varighet
 *    anslås til ~3 min per sett.
 *  - Løping: MET settes fra farten når distanse+tid finnes (10 km/t ≈ 10 MET),
 *    ellers 9. Kun distanse: ~1 kcal per kg per km (veletablert tommelregel).
 *  - Hiking får tillegg for stigning (arbeid mot tyngdekraften).
 * Alt er bevisst omtrentlig - uten pulsdata blir det aldri eksakt, og det er
 * bedre å vise et ærlig grovt tall enn ingenting.
 */
public final class WorkoutCalorieEstimator {

    private static final Map<String, Double> MET_BY_TYPE = Map.of(
            "STYRKE", 5.0,
            "BULDRING", 6.0,
            "FRISTIL", 5.0,
            "SVØMMING", 7.0,
            "SYKKEL", 7.5,
            "HIKING", 6.0,
            "LØPING", 9.0);

    private static final double MINUTES_PER_STRENGTH_SET = 3.0;
    private static final double CLIMB_KCAL_PER_KG_PER_M = 0.01;
    private static final double RUN_KCAL_PER_KG_PER_KM = 1.0;
    private static final double BIKE_KCAL_PER_KG_PER_KM = 0.4;
    private static final double HIKE_KCAL_PER_KG_PER_KM = 0.55;
    private static final double MIN_RUN_MET = 6.0;
    private static final double MAX_RUN_MET = 12.5;

    private WorkoutCalorieEstimator() {
    }

    public static int estimate(Workout workout, double weightKg) {
        String type = workout.getType() == null ? "" : workout.getType().toUpperCase();
        JsonNode c = workout.getContent();

        // Ekte kalorier (f.eks. fra Garmin-import med pulsdata) trumfer estimatet.
        double loggedKcal = c.path("kcal").asDouble(0);
        if (loggedKcal > 0) {
            return (int) Math.round(loggedKcal);
        }

        double durationMin = c.path("durationMin").asDouble(0);
        double distanceKm = c.path("distanceKm").asDouble(0);
        double ascentM = c.path("ascentM").asDouble(0);

        // Styrke logges oftest uten varighet - anslå fra antall sett.
        if (durationMin <= 0 && "STYRKE".equals(type)) {
            durationMin = countSets(c) * MINUTES_PER_STRENGTH_SET;
        }

        double kcal;
        if (durationMin > 0) {
            kcal = met(type, distanceKm, durationMin) * weightKg * (durationMin / 60.0);
        } else if (distanceKm > 0) {
            kcal = distanceKcal(type, distanceKm, weightKg);
        } else {
            return 0;
        }

        if ("HIKING".equals(type) && ascentM > 0) {
            kcal += CLIMB_KCAL_PER_KG_PER_M * weightKg * ascentM;
        }
        return (int) Math.round(kcal);
    }

    private static double met(String type, double distanceKm, double durationMin) {
        double base = MET_BY_TYPE.getOrDefault(type, 5.0);
        if ("LØPING".equals(type) && distanceKm > 0) {
            double kmh = distanceKm / (durationMin / 60.0);
            return Math.clamp(kmh, MIN_RUN_MET, MAX_RUN_MET); // 10 km/t ≈ 10 MET
        }
        return base;
    }

    private static double distanceKcal(String type, double km, double weightKg) {
        return switch (type) {
            case "LØPING" -> RUN_KCAL_PER_KG_PER_KM * weightKg * km;
            case "SYKKEL" -> BIKE_KCAL_PER_KG_PER_KM * weightKg * km;
            case "HIKING" -> HIKE_KCAL_PER_KG_PER_KM * weightKg * km;
            default -> 0;
        };
    }

    /** Totalt antall sett i en styrkeøkt, på tvers av blokk-typene. */
    private static int countSets(JsonNode content) {
        int sets = 0;
        for (JsonNode block : content.path("blocks")) {
            if ("superset".equals(block.path("kind").asText(""))) {
                for (JsonNode ex : block.path("exercises")) {
                    sets += ex.path("sets").size();
                }
            } else {
                sets += block.path("sets").size() + block.path("drops").size();
            }
        }
        return sets;
    }
}
