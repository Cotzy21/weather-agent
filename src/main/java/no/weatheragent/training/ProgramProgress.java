package no.weatheragent.training;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Hvor langt brukeren er i treningsprogrammet sitt («Dag 12/56»).
 *
 * @param status NOT_STARTED | ACTIVE | FINISHED
 * @param dayNumber 1-basert dagnummer, begrenset til 0 (ikke startet) ... totalDays
 */
public record ProgramProgress(LocalDate startDate, int weeks, int dayNumber, int totalDays, int weekNumber, String status) {

    public static final int MAX_WEEKS = 52;

    public static ProgramProgress of(LocalDate startDate, int weeks, LocalDate today) {
        if (startDate == null) throw new IllegalArgumentException("Mangler startdato.");
        if (weeks < 1 || weeks > MAX_WEEKS) {
            throw new IllegalArgumentException("Programmet må vare 1-" + MAX_WEEKS + " uker.");
        }
        int total = weeks * 7;
        long since = ChronoUnit.DAYS.between(startDate, today);
        if (since < 0) return new ProgramProgress(startDate, weeks, 0, total, 0, "NOT_STARTED");
        if (since >= total) return new ProgramProgress(startDate, weeks, total, total, weeks, "FINISHED");
        int day = (int) since + 1;
        return new ProgramProgress(startDate, weeks, day, total, (day - 1) / 7 + 1, "ACTIVE");
    }
}
