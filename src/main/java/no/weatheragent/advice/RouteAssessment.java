package no.weatheragent.advice;

import java.util.List;

/**
 * Terrengvurderingen av en planlagt rute: vanskelighetsgrad, nøkkeltall om
 * profilen, og konkrete utfordringer å være forberedt på.
 *
 * @param difficulty      DNT-inspirert gradering (grønn/blå/rød/svart)
 * @param highestPointM   høyeste punkt på ruta (moh)
 * @param maxGradientPct  bratteste parti, i prosent helning (~500 m-segmenter)
 * @param descentM        samlet nedstigning i meter
 * @param challenges      regelbaserte «vær forberedt på»-punkter
 */
public record RouteAssessment(
        Difficulty difficulty,
        double highestPointM,
        double maxGradientPct,
        double descentM,
        List<String> challenges
) {

    /** Gradering etter DNTs merkefarger, med norsk merkelapp for UI. */
    public enum Difficulty {
        GRONN("Enkel"),
        BLAA("Middels"),
        ROED("Krevende"),
        SVART("Ekspert");

        private final String label;

        Difficulty(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
