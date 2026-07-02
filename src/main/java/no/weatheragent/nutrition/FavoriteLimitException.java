package no.weatheragent.nutrition;

/** Kastes når en bruker prøver å lagre flere favoritter enn taket tillater. */
public class FavoriteLimitException extends RuntimeException {

    public FavoriteLimitException(String message) {
        super(message);
    }
}
