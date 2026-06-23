package no.weatheragent.assist;

import no.weatheragent.interpret.Interpretation;
import no.weatheragent.ranking.RankedPlaceOverPeriod;

import java.util.List;

/**
 * Svaret tur-assistenten gir på et fritekst-spørsmål: hva vi tolket spørsmålet
 * som, og den ferdige rangeringen av steder. Tom rangering betyr enten at vi
 * ikke fant en region, eller at det ikke fantes værdata for perioden.
 */
public record TurResult(Interpretation interpretation, List<RankedPlaceOverPeriod> ranking) {

    public boolean hasAnswer() {
        return !ranking.isEmpty();
    }
}
