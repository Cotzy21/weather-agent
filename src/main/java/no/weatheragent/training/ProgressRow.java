package no.weatheragent.training;

import java.time.LocalDate;

/** Projeksjon for progresjon per dag for én øvelse (fra en aggregert spørring). */
public interface ProgressRow {
    LocalDate getDate();

    Double getMaxWeight();

    Double getVolume();
}
