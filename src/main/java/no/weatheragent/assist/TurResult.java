package no.weatheragent.assist;

import no.weatheragent.hiking.Trail;
import no.weatheragent.interpret.Interpretation;
import no.weatheragent.ranking.RankedPlaceOverPeriod;

import java.util.List;

/**
 * Svaret tur-assistenten gir på et fritekst-spørsmål: hva vi tolket spørsmålet
 * som, den ferdige rangeringen av steder, merkede turruter i området, og
 * klær/utstyr-råd for vinneren. Tom rangering betyr enten at vi ikke fant en
 * region, eller at det ikke fantes værdata for perioden; da er også {@code trails}
 * og {@code clothing} tomme.
 */
public record TurResult(Interpretation interpretation,
                        List<RankedPlaceOverPeriod> ranking,
                        List<Trail> trails,
                        List<String> clothing) {

    public boolean hasAnswer() {
        return !ranking.isEmpty();
    }
}
