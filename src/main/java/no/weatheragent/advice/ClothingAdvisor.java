package no.weatheragent.advice;

import java.util.ArrayList;
import java.util.List;

/**
 * Foreslår klær og utstyr ut fra været på et sted. Ren, regelbasert og testbar -
 * ingen LLM (deterministiske råd er en jobb for kode, ikke en språkmodell).
 *
 * Tar de samme snitt-tallene som vises for vinneren: høyeste dagtemperatur,
 * nedbør per dag og gjennomsnittsvind.
 */
public final class ClothingAdvisor {

    private ClothingAdvisor() {
    }

    public static List<String> recommend(double tempC, double precipMmPerDay, double windMs) {
        List<String> tips = new ArrayList<>();

        // Lag etter temperatur.
        if (tempC < 0) {
            tips.add("Vinterklær: dunjakke, lue, votter og ullundertøy");
        } else if (tempC < 8) {
            tips.add("Kledd for kjølig vær: ullag, lue og votter");
        } else if (tempC < 15) {
            tips.add("Lett jakke eller softshell over et ullag");
        } else if (tempC < 22) {
            tips.add("T-skjorte og en lett genser å ta på");
        } else {
            tips.add("Lett og luftig tøy");
        }

        // Nedbør.
        if (precipMmPerDay > 5) {
            tips.add("Skikkelig regntøy (jakke + bukse) og vanntette sko");
        } else if (precipMmPerDay > 1) {
            tips.add("Pakk regnjakke – det kan komme nedbør");
        }

        // Vind.
        if (windMs > 8) {
            tips.add("Vindtett ytterlag – det blåser friskt");
        }

        // Sol (varmt og tørt).
        if (tempC >= 18 && precipMmPerDay < 1) {
            tips.add("Solkrem og solbriller");
        }

        // Gjelder alltid.
        tips.add("Gode tursko og nok drikke");

        return tips;
    }
}
