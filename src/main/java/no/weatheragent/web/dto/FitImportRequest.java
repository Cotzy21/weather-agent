package no.weatheragent.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import no.weatheragent.training.FitSession;

import java.util.List;

/** En bunke økter lest fra FIT-filer (nettleseren sender dem i små grupper). */
public record FitImportRequest(@NotEmpty @Size(max = 50) List<@Valid FitSession> sessions) {
}
