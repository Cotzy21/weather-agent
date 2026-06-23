package no.weatheragent.assist;

import no.weatheragent.hiking.Trail;
import no.weatheragent.interpret.Interpretation;
import no.weatheragent.ranking.RankedPlaceOverPeriod;

import java.util.List;

/**
 * Svaret tur-assistenten gir på et fritekst-spørsmål: hva vi tolket spørsmålet
 * som, den ferdige rangeringen av steder, og merkede turruter nær vinneren.
 * Tom rangering betyr enten at vi ikke fant en region, eller at det ikke fantes
 * værdata for perioden; da er også {@code trails} tom.
 */
public record TurResult(Interpretation interpretation,
                        List<RankedPlaceOverPeriod> ranking,
                        List<Trail> trails) {

    public boolean hasAnswer() {
        return !ranking.isEmpty();
    }
}
