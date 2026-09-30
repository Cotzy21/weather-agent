package no.weatheragent.training;

import java.time.LocalDate;

/**
 * Én aktivitet fra en Garmin Connect CSV-eksport, oversatt til appens verden:
 * typen er allerede mappet til våre økt-typer (LØPING, STYRKE ...), og
 * tallfeltene er null når kolonnen manglet eller var «--».
 *
 * Styrkeøkter har bare totaler i CSV-en (sett, repetisjoner, puls), aldri hvilke øvelser eller vekter;
 * de detaljene finnes bare i FIT-filene (se {@code FitSession}).
 */
public record GarminActivity(
        LocalDate date,
        String title,
        String type,
        Double distanceKm,
        Double durationMin,
        Double ascentM,
        Double kcal,
        Integer totalSets,
        Integer totalReps,
        Integer avgHr,
        Integer maxHr) {
}
