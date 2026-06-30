package no.weatheragent.training;

import java.time.LocalDate;

/** Progresjon for én øvelse en gitt dag: beste vekt og samlet volum (reps*vekt). */
public record ProgressPoint(LocalDate date, double maxWeight, double volume) {
}
