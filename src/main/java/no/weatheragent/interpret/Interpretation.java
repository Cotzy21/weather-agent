package no.weatheragent.interpret;

/**
 * Det strukturerte resultatet av å tolke et fritekst-spørsmål. Dette er alt
 * resten av kjeden trenger fra språk-laget (HANDOFF §3).
 *
 * @param region   området spørsmålet gjelder (administrativt område eller
 *                 nasjonalpark, hvor som helst i verden), med offisielt lokalt
 *                 navn - f.eks. "Møre og Romsdal", "Ålesund" eller "Tirol".
 *                 {@code null} hvis tolkeren ikke fant en region i spørsmålet.
 * @param country  ISO 3166-1 alpha-2-koden for landet regionen ligger i
 *                 (f.eks. "NO", "AT"). Brukes til å scope OSM-oppslaget så
 *                 navnekollisjoner på tvers av land unngås. {@code null} hvis
 *                 modellen ikke var sikker - da søkes det globalt på navn.
 * @param when     tidsuttrykket modellen klassifiserte (for innsyn/visning).
 * @param target   om brukeren vil rangere steder eller turruter.
 * @param dates    perioden, ferdig utregnet fra {@code when} (TimeExpressionResolver).
 * @param tripType ønsket turtype (fjell/lav/uansett).
 */
public record Interpretation(String region, String country, TimeExpression when,
                             Target target, DateRange dates, TripType tripType) {

    /** True hvis vi faktisk fant en region å slå opp. */
    public boolean hasRegion() {
        return region != null && !region.isBlank();
    }
}
