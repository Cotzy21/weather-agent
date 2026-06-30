package no.weatheragent.training;

/** Kastes når AI-forslaget ikke kunne tolkes (f.eks. ugyldig JSON fra modellen). */
public class AiSuggestionException extends RuntimeException {
    public AiSuggestionException(String message) {
        super(message);
    }
}
