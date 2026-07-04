package no.weatheragent.recovery;

import no.weatheragent.recovery.InjuryWarning.Level;
import no.weatheragent.training.Workout;
import no.weatheragent.training.WorkoutCalorieEstimator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Skadeforebygging: oppdager uvanlige volumhopp per aktivitetstype i
 * treningsloggen. Ren og testbar, null API-kall.
 *
 * Metoden er ACWR (acute:chronic workload ratio) fra idrettsforskningen
 * (Gabbett m.fl.), forenklet: belastningen siste 7 dager («akutt») deles på
 * gjennomsnittlig ukesbelastning de 4 ukene før («kronisk»). Forholdstall
 * over ~1,3 regnes som rask økning, over ~1,5 som faresonen. Tersklene er
 * tommelfingerregler - IKKE medisinske råd.
 *
 * Som belastningsmål brukes kaloriestimatet fra {@link WorkoutCalorieEstimator}:
 * det er samme enhet for alle økt-typer (km, sett og minutter kan ikke
 * sammenlignes direkte), og Garmin-importerte økter bidrar med EKTE
 * pulsbaserte kalorier. Kroppsvekten settes fast - den forkortes bort i
 * forholdstallet.
 */
public final class InjuryRiskAnalyzer {

    private static final double NOMINAL_WEIGHT_KG = 75;

    private static final int ACUTE_DAYS = 7;
    private static final int CHRONIC_WEEKS = 4;

    /** Under denne akutt-belastningen (kcal/uke) bryr vi oss ikke. */
    private static final double MIN_ACUTE_LOAD = 300;
    /** Under dette kronisk-nivået finnes det ikke noe «vanlig» å sammenligne med. */
    private static final double MIN_CHRONIC_LOAD = 100;

    private static final double MODERATE_RATIO = 1.3;
    private static final double HIGH_RATIO = 1.5;

    private static final Map<String, String> MESSAGE_BY_TYPE = Map.of(
            "LØPING", "Typiske overbelastningsskader for løpere er beinhinnebetennelse og akillesplager. "
                    + "Ro ned denne uka, og øk heller gradvis (~10 % per uke).",
            "STYRKE", "Rask volumøkning belaster sener og ledd før musklene protesterer. "
                    + "Vurder en lettere uke (deload) før du øker videre.",
            "BULDRING", "Fingersener og pulley-skader kommer typisk av raske volumhopp. "
                    + "Færre harde forsøk, mer teknikk denne uka.",
            "SYKKEL", "Knær tar støyten ved raske økninger på sykkelen. "
                    + "Ro ned intensiteten, og sjekk at setehøyden stemmer.",
            "SVØMMING", "Svømmerskulder er den klassiske overbelastningen. "
                    + "Reduser volumet litt og varier svømmetakene.",
            "HIKING", "Knær og ankler belastes hardt ved raske økninger, særlig i nedstigninger. "
                    + "Kortere turer neste uke, og bruk gjerne staver.");

    private static final String DEFAULT_MESSAGE =
            "Kroppen trenger tid til å tilpasse seg - øk gradvis (~10 % per uke).";

    private static final String NEW_ACTIVITY_MESSAGE =
            "Ny eller gjenopptatt aktivitet - sener og ledd tilpasser seg saktere enn kondisjonen. "
                    + "Bygg gradvis (~10 % økning per uke) de første ukene.";

    private InjuryRiskAnalyzer() {
    }

    public static List<InjuryWarning> analyze(List<Workout> workouts, LocalDate today) {
        // Belastning per type, delt i akutt (siste 7 dager) og kronisk (4 uker før).
        Map<String, double[]> loads = new HashMap<>(); // [akutt, kronisk-total]
        LocalDate acuteFrom = today.minusDays(ACUTE_DAYS - 1);
        LocalDate chronicFrom = acuteFrom.minusWeeks(CHRONIC_WEEKS);

        for (Workout w : workouts) {
            LocalDate date = w.getDate();
            if (date.isBefore(chronicFrom) || date.isAfter(today)) {
                continue;
            }
            String type = w.getType() == null ? "" : w.getType().toUpperCase(Locale.ROOT);
            double load = WorkoutCalorieEstimator.estimate(w, NOMINAL_WEIGHT_KG);
            double[] acc = loads.computeIfAbsent(type, t -> new double[2]);
            if (date.isBefore(acuteFrom)) {
                acc[1] += load;
            } else {
                acc[0] += load;
            }
        }

        List<InjuryWarning> warnings = new ArrayList<>();
        loads.forEach((type, acc) -> {
            double acute = acc[0];
            double chronicWeekly = acc[1] / CHRONIC_WEEKS;
            if (acute < MIN_ACUTE_LOAD) {
                return;
            }
            if (chronicWeekly < MIN_CHRONIC_LOAD) {
                warnings.add(new InjuryWarning(type, Level.NY_AKTIVITET, 0, NEW_ACTIVITY_MESSAGE));
                return;
            }
            double ratio = acute / chronicWeekly;
            if (ratio >= MODERATE_RATIO) {
                Level level = ratio >= HIGH_RATIO ? Level.HOY : Level.MODERAT;
                int percent = (int) Math.round((ratio - 1) * 100);
                warnings.add(new InjuryWarning(type, level, percent,
                        MESSAGE_BY_TYPE.getOrDefault(type, DEFAULT_MESSAGE)));
            }
        });

        // Verste først, så det viktigste står øverst i UI-et.
        warnings.sort((a, b) -> Integer.compare(b.percentAboveNormal(), a.percentAboveNormal()));
        return warnings;
    }
}
