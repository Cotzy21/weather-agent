package no.weatheragent.training;

import java.util.List;

/**
 * Et AI-generert treningsforslag som kan bestå av FLERE økter - en komplett
 * plan. F.eks. gir «push pull legs» tre økter, «upper/lower» to. Hver økt har
 * samme form som et enkelt {@link Suggestion} (og som en lagret økt), så
 * frontenden kan vise hver økt for seg og lagre/sende den til byggeren.
 *
 * @param title    kort navn på planen (f.eks. «Push/Pull/Legs»)
 * @param summary  1-2 setninger om planen som helhet
 * @param workouts øktene i planen, i rekkefølge
 */
public record PlanSuggestion(String title, String summary, List<Suggestion> workouts) {
}
