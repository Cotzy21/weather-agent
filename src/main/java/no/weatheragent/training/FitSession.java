package no.weatheragent.training;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * En styrkeøkt lest fra en Garmin FIT-fil i nettleseren: hvilke øvelser, og reps og vekt per sett.
 * Det er dette CSV-eksporten mangler, og det som trengs for å regne ut progresjon per øvelse. Nettleseren
 * gjør den tunge jobben (pakke ut, lese FIT), serveren mottar bare små, ferdig tolkede økter.
 *
 * @param clientId avledet av starttidspunktet, så samme økt aldri importeres to ganger
 */
public record FitSession(
        @NotNull UUID clientId,
        @NotNull LocalDate date,
        @Size(max = 120) String title,
        @DecimalMin("0") @DecimalMax("1440") Double durationMin,
        @DecimalMin("0") @DecimalMax("20000") Double kcal,
        @Min(0) @Max(260) Integer avgHr,
        @Min(0) @Max(260) Integer maxHr,
        @NotEmpty @Size(max = 60) List<@Valid Block> blocks) {

    public record Block(@NotBlank @Size(max = 80) String name, @NotEmpty @Size(max = 100) List<@Valid SetEntry> sets) {
    }

    public record SetEntry(@Min(1) @Max(1000) int reps, @DecimalMin("0") @DecimalMax("1000") double weightKg) {
    }
}
