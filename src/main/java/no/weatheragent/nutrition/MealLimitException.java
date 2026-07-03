package no.weatheragent.nutrition;

/** Kastes når en bruker prøver å lagre flere egne måltider enn taket tillater. */
public class MealLimitException extends RuntimeException {

    public MealLimitException(String message) {
        super(message);
    }
}
