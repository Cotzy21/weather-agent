package no.weatheragent.training;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Activity summary from a user-selected Garmin Connect result, with optional decoded FIT data. */
public record GarminLogEntry(
        @Positive long activityId,
        @NotNull LocalDate date,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 50) String type,
        @DecimalMin("0") @DecimalMax("10000") Double distanceKm,
        @DecimalMin("0") @DecimalMax("14400") Double durationMin,
        @DecimalMin("0") @DecimalMax("100000") Double ascentM,
        @DecimalMin("0") @DecimalMax("20000") Double kcal,
        @Min(0) @Max(10000) Integer totalSets,
        @Min(0) @Max(100000) Integer totalReps,
        @Min(0) @Max(260) Integer avgHr,
        @Min(0) @Max(260) Integer maxHr,
        @Valid FitSession strength) {

    GarminActivity activity() {
        return new GarminActivity(date, title.strip(), GarminCsvParser.mapType(type), distanceKm, durationMin,
                ascentM, kcal, totalSets, totalReps, avgHr, maxHr);
    }
}
