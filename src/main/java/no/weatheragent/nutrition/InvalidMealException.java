package no.weatheragent.nutrition;

/** Kastes når et innsendt måltid ikke gir mening (tom/for stor ingrediensliste). */
public class InvalidMealException extends RuntimeException {

    public InvalidMealException(String message) {
        super(message);
    }
}
