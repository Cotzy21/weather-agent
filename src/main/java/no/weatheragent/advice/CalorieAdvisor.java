package no.weatheragent.advice;

import java.util.ArrayList;
import java.util.List;

/**
 * Grovt estimat av tid, kaloriforbruk og mat/drikke for en fottur. Ren og
 * testbar - ingen nettverk.
 *
 * Modell (bevisst enkel):
 *  - Tid: distanse / 4 km/t + Naismith-tillegg (600 høydemeter per time).
 *  - Kalorier: ~6 MET fottur (MET·kg·timer) pluss arbeid for stigningen.
 */
public final class CalorieAdvisor {

    private static final double HIKING_KMH = 4.0;
    private static final double MET_HIKING = 6.0;
    private static final double ASCENT_M_PER_HOUR = 600.0;     // Naismiths regel
    private static final double CLIMB_KCAL_PER_KG_PER_M = 0.01; // arbeid mot tyngdekraften
    private static final double DEFAULT_WEIGHT_KG = 75;

    private CalorieAdvisor() {
    }

    public static RouteEstimate estimate(double distanceKm, double ascentM, double weightKg) {
        double d = Math.max(0, distanceKm);
        double a = Math.max(0, ascentM);
        double w = weightKg > 0 ? weightKg : DEFAULT_WEIGHT_KG;

        double hours = d / HIKING_KMH + a / ASCENT_M_PER_HOUR;
        double flatKcal = MET_HIKING * w * hours;
        double climbKcal = CLIMB_KCAL_PER_KG_PER_M * w * a;
        int calories = (int) Math.round(flatKcal + climbKcal);

        return new RouteEstimate(round1(d), Math.round(a), round1(hours), calories, snacks(calories));
    }

    private static List<String> snacks(int calories) {
        List<String> s = new ArrayList<>();
        s.add("Drikk jevnlig – minst 0,5 liter vann per time");
        if (calories > 0) {
            int cover = (int) Math.round(calories * 0.5); // dekk ca. halvparten underveis
            int bars = (int) Math.ceil(cover / 250.0);
            int bananas = (int) Math.ceil(cover / 105.0);
            s.add("Ta med ca. " + cover + " kcal mellommat – f.eks. "
                    + bars + (bars == 1 ? " energibar" : " energibarer")
                    + " eller " + bananas + (bananas == 1 ? " banan" : " bananer"));
        }
        return s;
    }

    private static double round1(double x) {
        return Math.round(x * 10) / 10.0;
    }
}
