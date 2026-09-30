package no.weatheragent.web.dto;

import no.weatheragent.training.WorkoutImportService;

/** {@code merged}: økter som fantes fra CSV-en og nå har fått øvelser og vekter. */
public record FitImportResultDto(int imported, int merged, int skipped) {

    public static FitImportResultDto from(WorkoutImportService.FitImportResult r) {
        return new FitImportResultDto(r.imported(), r.merged(), r.skipped());
    }
}
