package no.weatheragent.web.dto;

import no.weatheragent.training.WorkoutImportService;

/** Resultatet av en Garmin-import: hvor mange økter som kom inn, ble hoppet over, eller ble slått sammen med en FIT-økt. */
public record ImportResultDto(int imported, int skipped, int merged) {

    public static ImportResultDto from(WorkoutImportService.ImportResult r) {
        return new ImportResultDto(r.imported(), r.skipped(), r.merged());
    }
}
