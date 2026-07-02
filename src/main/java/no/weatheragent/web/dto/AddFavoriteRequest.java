package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Klientens ønske om å favorittmerke en matvare. Lengder matcher DB-kolonnene. */
public record AddFavoriteRequest(
        @NotBlank @Size(max = 120) String key,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 16) String emoji,
        @Size(max = 40) String tag) {
}
