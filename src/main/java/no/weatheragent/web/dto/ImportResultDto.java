package no.weatheragent.web.dto;

import no.weatheragent.training.WorkoutImportService;

/** Resultatet av en Garmin-import: hvor mange økter som kom inn / ble hoppet over. */
public record ImportResultDto(int imported, int skipped) {

    public static ImportResultDto from(WorkoutImportService.ImportResult r) {
        return new ImportResultDto(r.imported(), r.skipped());
    }
}
