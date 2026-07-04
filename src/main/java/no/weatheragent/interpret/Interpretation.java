package no.weatheragent.interpret;

import no.weatheragent.ranking.ScoreWeights;

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
 * @param weights  vekter UTLEDET AV SPØRSMÅLET («vi hater regn og vind» ->
 *                 regn/vind høy), eller {@code null} når spørsmålet ikke
 *                 uttrykker preferanser. Manuelle UI-valg vinner alltid over
 *                 disse (håndheves i TurvaerService).
 */
public record Interpretation(String region, String country, TimeExpression when,
                             Target target, DateRange dates, TripType tripType,
                             ScoreWeights weights) {

    /** Uten vekter - beholder alle eksisterende kallsteder og tester. */
    public Interpretation(String region, String country, TimeExpression when,
                          Target target, DateRange dates, TripType tripType) {
        this(region, country, when, target, dates, tripType, null);
    }

    /** True hvis vi faktisk fant en region å slå opp. */
    public boolean hasRegion() {
        return region != null && !region.isBlank();
    }
}
