package no.weatheragent.nutrition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Strekkodeoppslag i tre trinn: 1) brukerens egne og offentlig delte matvarer, 2) Open Food Facts, 3) ikke funnet.
 * Et treff fra Open Food Facts lagres som en PRIVAT egen matvare for brukeren (med strekkoden), så dagboka kan
 * logge den som alle andre egne varer, og neste skanning treffer steg 1 uten nytt eksternt kall. Brukeren kan
 * slette eller rette den som andre egne varer. Feil hos Open Food Facts gir bare «ikke funnet», aldri en feilside.
 */
@Service
public class BarcodeLookupService {

    private static final Logger log = LoggerFactory.getLogger(BarcodeLookupService.class);

    /** {@code importedFromOpenFoodFacts}: varen ble akkurat hentet derfra (UI-et viser kildehenvisning). */
    public record Result(CustomFood food, boolean importedFromOpenFoodFacts) {
    }

    private final CustomFoodService customFoods;
    private final OpenFoodFactsClient openFoodFacts;
    private final boolean openFoodFactsEnabled;

    public BarcodeLookupService(CustomFoodService customFoods, OpenFoodFactsClient openFoodFacts,
                                @Value("${openfoodfacts.enabled:true}") boolean openFoodFactsEnabled) {
        this.customFoods = customFoods;
        this.openFoodFacts = openFoodFacts;
        this.openFoodFactsEnabled = openFoodFactsEnabled;
    }

    public Optional<Result> lookup(UUID userId, String rawBarcode) {
        String barcode = CustomFoodService.normalizeBarcode(rawBarcode);
        if (barcode == null) {
            return Optional.empty();
        }
        Optional<CustomFood> known = customFoods.byBarcode(userId, barcode);
        if (known.isPresent()) {
            return Optional.of(new Result(known.get(), false));
        }
        if (!openFoodFactsEnabled) {
            return Optional.empty();
        }
        Optional<OpenFoodFactsProduct> product;
        try {
            product = openFoodFacts.lookup(barcode);
        } catch (RuntimeException e) {
            log.warn("Strekkodeoppslag hos Open Food Facts feilet ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
        return product.map(p -> {
            CustomFood saved = customFoods.create(userId, p.name(), p.brand(), barcode,
                    p.kcalPer100g(), p.proteinG(), p.fatG(), p.carbG(),
                    p.portionGrams() == null ? null : "porsjon", p.portionGrams(), false);
            return new Result(saved, true);
        });
    }
}
