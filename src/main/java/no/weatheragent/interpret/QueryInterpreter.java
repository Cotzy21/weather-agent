package no.weatheragent.interpret;

/**
 * Oversetter et fritekst-spørsmål ("hvor i Møre og Romsdal blir det best vær
 * i helga") til strukturerte data resten av kjeden kan bruke.
 *
 * Bevisst et grensesnitt: vi starter med en lokal LLM ({@link LlmQueryInterpreter}),
 * men kan bytte til en regelbasert eller sky-basert implementasjon uten at
 * resten av appen merker det (HANDOFF §6).
 */
public interface QueryInterpreter {

    /** Tolk spørsmålet. Kaster hvis tolkningen ikke kan fullføres. */
    Interpretation interpret(String text);
}
