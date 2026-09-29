package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ny egen matvare. Næring er per 100 g; porsjon og strekkode er valgfrie.
 * Grensene for tallene sjekkes i CustomFoodService.
 */
public record CreateCustomFoodRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 80) String brand,
        @Size(max = 32) String barcode,
        double kcalPer100g,
        double proteinPer100g,
        double fatPer100g,
        double carbPer100g,
        @Size(max = 40) String portionName,
        Double portionGrams,
        boolean isPublic
) {
}
