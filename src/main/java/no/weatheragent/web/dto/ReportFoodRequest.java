package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import no.weatheragent.nutrition.ReportReason;

/** Rapport om en offentlig delt matvare. Notatet er valgfritt. */
public record ReportFoodRequest(@NotNull ReportReason reason, @Size(max = 300) String note) {
}
