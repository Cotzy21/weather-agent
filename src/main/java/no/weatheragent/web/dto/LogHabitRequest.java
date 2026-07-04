package no.weatheragent.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Sett dagens verdi for en vane. Verdi <= 0 fjerner loggen for dagen. */
public record LogHabitRequest(@NotNull LocalDate date, double value) {
}
