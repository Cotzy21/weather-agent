package no.weatheragent.nutrition;

/** Kastes når en logge-forespørsel peker på en foodId som ikke finnes i Matvaretabellen. */
public class UnknownFoodException extends RuntimeException {

    public UnknownFoodException(String foodId) {
        super("Fant ikke matvaren (" + foodId + ") i Matvaretabellen.");
    }
}
