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
 * @param nutrientId  Matvaretabellens id (f.eks. "Vit C", "Ca", "I")
 * @param displayName norsk visningsnavn
 * @param unit        enheten Matvaretabellen bruker for stoffet
 * @param dailyTarget anbefalt daglig inntak (samme enhet)
 * @param lowAdvice   hva lavt inntak over tid kan føre til + gode kilder
 */
public record NutrientReference(
        String nutrientId,
        String displayName,
        String unit,
        double dailyTarget,
        String lowAdvice
) {

    public static final List<NutrientReference> TRACKED = List.of(
            new NutrientReference("Fiber", "Fiber", "g", 30,
                    "Lite fiber kan gi treg mage og påvirker blodsukker og kolesterol. "
                            + "Gode kilder: grovbrød, havregryn, bønner, frukt og grønnsaker."),
            new NutrientReference("Vit A", "Vitamin A", "RAE", 700,
                    "Lite vitamin A kan svekke nattsyn og immunforsvar. "
                            + "Gode kilder: gulrot, lever, egg, meieriprodukter."),
            new NutrientReference("Vit B1", "Tiamin (B1)", "mg", 1.2,
                    "Lite tiamin kan gi trøtthet og konsentrasjonsvansker. "
                            + "Gode kilder: fullkorn, svinekjøtt, belgfrukter."),
            new NutrientReference("Vit B2", "Riboflavin (B2)", "mg", 1.4,
                    "Lite riboflavin kan gi sprukne lepper og såre munnviker. "
                            + "Gode kilder: melk, egg, grønne grønnsaker."),
            new NutrientReference("Vit B6", "Vitamin B6", "mg", 1.6,
                    "Lite B6 kan påvirke humør og immunforsvar. "
                            + "Gode kilder: fjørfe, fisk, banan, potet."),
            new NutrientReference("Vit B12", "Vitamin B12", "µg", 4,
                    "Lite B12 kan gi trøtthet og nummenhet, og er vanlig ved plantebasert kost. "
                            + "Gode kilder: kjøtt, fisk, egg, meieri - eller tilskudd."),
            new NutrientReference("Folat", "Folat", "µg", 330,
                    "Lite folat kan gi blodmangel; ekstra viktig ved graviditet. "
                            + "Gode kilder: grønne bladgrønnsaker, bønner, appelsin."),
            new NutrientReference("Vit C", "Vitamin C", "mg", 100,
                    "Lite vitamin C svekker immunforsvar og jernopptak. "
                            + "Gode kilder: paprika, sitrus, bær, brokkoli."),
            new NutrientReference("Vit D", "Vitamin D", "µg", 10,
                    "Lite vitamin D svekker skjelett og immunforsvar - vanlig i Norge om vinteren. "
                            + "Gode kilder: fet fisk, tran, beriket melk."),
            new NutrientReference("Ca", "Kalsium", "mg", 950,
                    "Lite kalsium svekker skjelettet over tid. "
                            + "Gode kilder: melk, ost, yoghurt, grønnkål."),
            new NutrientReference("Fe", "Jern", "mg", 12,
                    "Lite jern kan gi blodmangel med trøtthet og svimmelhet. "
                            + "Gode kilder: rødt kjøtt, lever, bønner, spinat (med noe C-vitamin til)."),
            new NutrientReference("Mg", "Magnesium", "mg", 350,
                    "Lite magnesium kan gi muskelkramper og trøtthet. "
                            + "Gode kilder: nøtter, fullkorn, mørk sjokolade."),
            new NutrientReference("K", "Kalium", "mg", 3500,
                    "Lite kalium kan påvirke blodtrykk og muskelfunksjon. "
                            + "Gode kilder: potet, banan, bønner, fisk."),
            new NutrientReference("Zn", "Sink", "mg", 10,
                    "Lite sink kan svekke immunforsvar og sårheling. "
                            + "Gode kilder: kjøtt, skalldyr, frø, nøtter."),
            new NutrientReference("Se", "Selen", "µg", 75,
                    "Lite selen kan svekke immunforsvar og stoffskifte. "
                            + "Gode kilder: fisk, egg, paranøtter (1-2 om dagen holder)."),
            new NutrientReference("I", "Jod", "µg", 150,
                    "Lite jod påvirker stoffskiftet; vanlig ved lite fisk/meieri i kosten. "
                            + "Gode kilder: hvit fisk, melk, jodberiket salt."));

    /** Andel av dagsmålet et snittinntak dekker, i prosent (0 hvis mål mangler). */
    public double percentOfTarget(double dailyAverage) {
        return dailyTarget <= 0 ? 0 : (dailyAverage / dailyTarget) * 100.0;
    }
}
