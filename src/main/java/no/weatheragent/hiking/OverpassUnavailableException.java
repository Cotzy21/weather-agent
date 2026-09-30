package no.weatheragent.hiking;

/**
 * Kastes når ingen av Overpass-instansene svarte i tide (alle travle/utilgjengelige, eller tidsgrensen gikk ut). Skiller
 * seg fra en feil i VÅR spørring: denne betyr «prøv igjen om litt», og vises som en vennlig melding til brukeren
 * (503) i stedet for at forespørselen henger til vertens tidsgrense og blir til en tom 502-side.
 */
public class OverpassUnavailableException extends RuntimeException {

    public static final String USER_MESSAGE =
            "Kartdata-tjenesten (OpenStreetMap) er travel eller utilgjengelig akkurat nå. Vent litt og prøv igjen.";

    public OverpassUnavailableException(Throwable cause) {
        super(USER_MESSAGE, cause);
    }
}
