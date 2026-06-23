package no.weatheragent.interpret;

/**
 * Det strukturerte resultatet av å tolke et fritekst-spørsmål. Dette er alt
 * resten av kjeden trenger fra språk-laget (HANDOFF §3).
 *
 * @param region   norsk område (fylke/kommune/nasjonalpark), f.eks. "Møre og
 *                 Romsdal" eller "Ålesund". {@code null} hvis tolkeren ikke fant
 *                 en region i spørsmålet.
 * @param when     tidsuttrykket modellen klassifiserte (for innsyn/visning).
 * @param target   om brukeren vil rangere steder eller turruter.
 * @param dates    perioden, ferdig utregnet fra {@code when} (TimeExpressionResolver).
 * @param tripType ønsket turtype (fjell/lav/uansett).
 */
public record Interpretation(String region, TimeExpression when, Target target,
                             DateRange dates, TripType tripType) {

    /** True hvis vi faktisk fant en region å slå opp. */
    public boolean hasRegion() {
        return region != null && !region.isBlank();
    }
}
