package no.weatheragent.interpret;

/**
 * Hvilket modell-nivå et LLM-kall skal bruke. Poenget er kostnad: stram
 * ekstraksjon (værsøk-tolkning) klarer seg fint med en billig/rask modell,
 * mens resonnering over treningshistorikk fortjener en bedre en.
 *
 * Nivå -> modellnavn styres av {@code llm.model.fast} / {@code llm.model.smart}
 * (begge faller tilbake til {@code llm.model}, så lokal LM Studio/Ollama med
 * én modell fungerer som før).
 */
public enum LlmTier {
    /** Billig/rask modell for enkle, stramme oppgaver (f.eks. JSON-ekstraksjon). */
    FAST,
    /** Dyrere modell for oppgaver som krever resonnering (f.eks. treningsforslag). */
    SMART
}
