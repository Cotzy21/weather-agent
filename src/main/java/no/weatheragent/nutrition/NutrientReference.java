package no.weatheragent.nutrition;

import java.util.List;

/**
 * Referanseverdier og råd for næringsstoffene vi følger med på i ukesoversikten.
 *
 * Verdiene er OMTRENTLIGE anbefalte daglige inntak for voksne, basert på
 * nordiske næringsstoffanbefalinger (NNR 2023). De varierer egentlig med kjønn,
 * alder osv. - når brukerprofiler får den infoen kan verdiene differensieres.
 * Alt er hardkodet med vilje: null API-kall, null driftskostnad.
 *
 * Rådet er delt i konsekvens + kildeliste, så kildene kan TILPASSES brukeren:
 * en melkeallergiker skal få grønnkål og beriket plantedrikk som kalsiumkilder,
 * ikke melk og ost (se {@link #lowAdvice(DietProfile)}). Hver liste har derfor
 * bevisst alternativer på tvers av allergener og dietter.
 *
 * @param nutrientId  Matvaretabellens id (f.eks. "Vit C", "Ca", "I")
 * @param displayName norsk visningsnavn
 * @param unit        enheten Matvaretabellen bruker for stoffet
 * @param dailyTarget anbefalt daglig inntak (samme enhet)
 * @param consequence hva lavt inntak over tid kan føre til
 * @param sources     gode kilder, i prioritert rekkefølge
 */
public record NutrientReference(
        String nutrientId,
        String displayName,
        String unit,
        double dailyTarget,
        String consequence,
        List<String> sources
) {

    /** Hvor mange kilder rådet nevner (de første som passer brukeren). */
    private static final int MAX_SOURCES = 5;

    public static final List<NutrientReference> TRACKED = List.of(
            new NutrientReference("Fiber", "Fiber", "g", 30,
                    "Lite fiber kan gi treg mage og påvirker blodsukker og kolesterol.",
                    List.of("grovbrød", "havregryn", "bønner", "linser", "frukt", "grønnsaker")),
            new NutrientReference("Vit A", "Vitamin A", "RAE", 700,
                    "Lite vitamin A kan svekke nattsyn og immunforsvar.",
                    List.of("gulrot", "søtpotet", "spinat", "lever", "egg", "meieriprodukter")),
            new NutrientReference("Vit B1", "Tiamin (B1)", "mg", 1.2,
                    "Lite tiamin kan gi trøtthet og konsentrasjonsvansker.",
                    List.of("fullkorn", "svinekjøtt", "belgfrukter", "solsikkefrø", "havregryn")),
            new NutrientReference("Vit B2", "Riboflavin (B2)", "mg", 1.4,
                    "Lite riboflavin kan gi sprukne lepper og såre munnviker.",
                    List.of("melk", "egg", "mandler", "sopp", "grønne grønnsaker")),
            new NutrientReference("Vit B6", "Vitamin B6", "mg", 1.6,
                    "Lite B6 kan påvirke humør og immunforsvar.",
                    List.of("fjørfe", "fisk", "banan", "potet", "kikerter")),
            new NutrientReference("Vit B12", "Vitamin B12", "µg", 4,
                    "Lite B12 kan gi trøtthet og nummenhet, og er vanlig ved plantebasert kost.",
                    List.of("kjøtt", "fisk", "egg", "meieriprodukter", "B12-beriket plantedrikk", "B12-tilskudd")),
            new NutrientReference("Folat", "Folat", "µg", 330,
                    "Lite folat kan gi blodmangel; ekstra viktig ved graviditet.",
                    List.of("grønne bladgrønnsaker", "bønner", "linser", "appelsin", "avokado")),
            new NutrientReference("Vit C", "Vitamin C", "mg", 100,
                    "Lite vitamin C svekker immunforsvar og jernopptak.",
                    List.of("paprika", "sitrus", "bær", "brokkoli", "kiwi")),
            new NutrientReference("Vit D", "Vitamin D", "µg", 10,
                    "Lite vitamin D svekker skjelett og immunforsvar - vanlig i Norge om vinteren.",
                    List.of("fet fisk", "tran", "egg", "beriket melk", "D-vitamin-beriket plantedrikk",
                            "D-vitamintilskudd")),
            new NutrientReference("Ca", "Kalsium", "mg", 950,
                    "Lite kalsium svekker skjelettet over tid.",
                    List.of("melk", "ost", "yoghurt", "grønnkål", "brokkoli", "mandler",
                            "kalsiumberiket plantedrikk", "sardiner")),
            new NutrientReference("Fe", "Jern", "mg", 12,
                    "Lite jern kan gi blodmangel med trøtthet og svimmelhet. Spis gjerne litt C-vitamin til.",
                    List.of("rødt kjøtt", "lever", "bønner", "linser", "spinat", "gresskarkjerner")),
            new NutrientReference("Mg", "Magnesium", "mg", 350,
                    "Lite magnesium kan gi muskelkramper og trøtthet.",
                    List.of("nøtter", "fullkorn", "bønner", "mørk sjokolade", "gresskarkjerner")),
            new NutrientReference("K", "Kalium", "mg", 3500,
                    "Lite kalium kan påvirke blodtrykk og muskelfunksjon.",
                    List.of("potet", "banan", "bønner", "fisk", "tomat")),
            new NutrientReference("Zn", "Sink", "mg", 10,
                    "Lite sink kan svekke immunforsvar og sårheling.",
                    List.of("kjøtt", "skalldyr", "gresskarkjerner", "nøtter", "havregryn", "kikerter")),
            new NutrientReference("Se", "Selen", "µg", 75,
                    "Lite selen kan svekke immunforsvar og stoffskifte.",
                    List.of("fisk", "egg", "paranøtter (1-2 om dagen holder)", "solsikkefrø", "sopp")),
            new NutrientReference("I", "Jod", "µg", 150,
                    "Lite jod påvirker stoffskiftet; vanlig ved lite fisk/meieri i kosten.",
                    List.of("hvit fisk", "melk", "egg", "jodberiket salt", "jodtilskudd")));

    /** Andel av dagsmålet et snittinntak dekker, i prosent (0 hvis mål mangler). */
    public double percentOfTarget(double dailyAverage) {
        return dailyTarget <= 0 ? 0 : (dailyAverage / dailyTarget) * 100.0;
    }

    /** Rådet uten tilpasning (brukeren har ingen preferanser satt). */
    public String lowAdvice() {
        return lowAdvice(DietProfile.NONE);
    }

    /**
     * Rådet tilpasset brukeren: kilder som bryter med allergier, diett eller
     * «misliker» filtreres bort. Blir det ingen igjen, pekes det mot tilskudd
     * og fagfolk i stedet for å anbefale noe brukeren ikke kan spise.
     */
    public String lowAdvice(DietProfile profile) {
        List<String> ok = sources.stream()
                .filter(s -> FoodFlags.allowed(s, profile))
                .limit(MAX_SOURCES)
                .toList();
        if (ok.isEmpty()) {
            return consequence + " Snakk gjerne med lege eller ernæringsfysiolog om tilskudd.";
        }
        return consequence + " Gode kilder: " + String.join(", ", ok) + ".";
    }
}
