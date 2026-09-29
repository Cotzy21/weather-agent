package no.weatheragent.training;

import java.time.LocalDate;

/**
 * Dagsform ut fra søvn: nivå, søvnen siste natt, snittet siste tre netter og
 * et konkret treningsråd.
 *
 * @param level          GOD, MIDDELS eller LAV
 * @param nightDate      datoen søvnen er logget på (i dag, eller i går hvis i dag mangler)
 * @param lastNightHours timer søvn den natta
 * @param avg3Hours      snitt av inntil tre netter fram til og med {@code nightDate}
 * @param advice         råd på valgt språk
 */
public record Readiness(Level level, LocalDate nightDate, double lastNightHours, double avg3Hours, String advice) {

    public enum Level { GOD, MIDDELS, LAV }
}
