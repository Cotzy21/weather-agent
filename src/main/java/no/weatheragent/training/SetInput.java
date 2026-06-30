package no.weatheragent.training;

/** Inndata for ett sett når en økt logges (frikoblet fra web-laget). */
public record SetInput(String exercise, int reps, double weightKg) {
}
