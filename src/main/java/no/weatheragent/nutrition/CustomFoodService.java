package no.weatheragent.nutrition;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Egne matvarer: opprett (privat eller delt offentlig), list, slett, søk og
 * strekkodeoppslag. Brukeren ser alltid sine egne + alle offentlig delte.
 *
 * Validering er streng med vilje: offentlige varer havner i alles søk, så
 * tallene må i det minste være fysisk mulige (maks 900 kcal og 100 g makro
 * per 100 g, og makroene kan ikke veie mer enn varen).
 */
@Service
public class CustomFoodService {

    static final int MAX_PER_USER = 200;
    private static final int SEARCH_LIMIT = 10;
    private static final double MAX_KCAL_PER_100G = 900;

    private final CustomFoodRepository repository;

    public CustomFoodService(CustomFoodRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomFood create(UUID ownerId, String name, String brand, String barcode,
                             double kcal, double protein, double fat, double carb,
                             String portionName, Double portionGrams, boolean isPublic) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Matvaren må ha et navn.");
        }
        if (kcal < 0 || kcal > MAX_KCAL_PER_100G) {
            throw new IllegalArgumentException("Kalorier per 100 g må være mellom 0 og 900.");
        }
        if (protein < 0 || fat < 0 || carb < 0 || protein + fat + carb > 100.5) {
            throw new IllegalArgumentException("Protein, fett og karbo per 100 g kan til sammen ikke overstige 100 g.");
        }
        if (portionGrams != null && (portionGrams <= 0 || portionGrams > 5000)) {
            throw new IllegalArgumentException("Porsjonen må være mellom 0 og 5000 g.");
        }
        String cleanBarcode = normalizeBarcode(barcode);
        if (barcode != null && !barcode.isBlank() && cleanBarcode == null) {
            throw new IllegalArgumentException("Strekkoden må være 8-14 siffer (EAN/UPC).");
        }
        if (repository.countByOwnerId(ownerId) >= MAX_PER_USER) {
            throw new IllegalArgumentException(
                    "Du har allerede " + MAX_PER_USER + " egne matvarer - slett noen først.");
        }
        return repository.save(new CustomFood(ownerId, name.trim(), blankToNull(brand), cleanBarcode,
                kcal, protein, fat, carb, blankToNull(portionName),
                blankToNull(portionName) == null ? null : portionGrams, isPublic));
    }

    @Transactional(readOnly = true)
    public List<CustomFood> listMine(UUID ownerId) {
        return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional
    public boolean delete(UUID id, UUID ownerId) {
        return repository.deleteByIdAndOwnerId(id, ownerId) > 0;
    }

    /** Egne + offentlige varer som matcher søket, egne først. */
    @Transactional(readOnly = true)
    public List<CustomFood> search(UUID userId, String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        return repository.searchVisible(userId, q, PageRequest.of(0, SEARCH_LIMIT));
    }

    /** Første synlige vare med strekkoden (egne foretrekkes), om noen. */
    @Transactional(readOnly = true)
    public Optional<CustomFood> byBarcode(UUID userId, String barcode) {
        String clean = normalizeBarcode(barcode);
        if (clean == null) {
            return Optional.empty();
        }
        return repository.findVisibleByBarcode(userId, clean).stream().findFirst();
    }

    /**
     * Slå opp en egen vare fra dagbok-id-en {@code egen:<uuid>} - bare hvis
     * brukeren eier den eller den er offentlig.
     */
    @Transactional(readOnly = true)
    public Optional<CustomFood> usable(UUID userId, String foodId) {
        if (foodId == null || !foodId.startsWith(CustomFood.ID_PREFIX)) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(foodId.substring(CustomFood.ID_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return repository.findById(id).filter(f -> f.getOwnerId().equals(userId) || f.isPublic());
    }

    /** Bare sifre, 8-14 lang (EAN-8, UPC-A, EAN-13, GTIN-14), ellers null. */
    static String normalizeBarcode(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\s", "");
        return digits.matches("\\d{8,14}") ? digits : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
