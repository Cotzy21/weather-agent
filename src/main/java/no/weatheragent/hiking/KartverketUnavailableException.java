package no.weatheragent.hiking;

/** An essential national map-data lookup failed. Do not pretend it returned no places. */
public class KartverketUnavailableException extends RuntimeException {
    public KartverketUnavailableException(Throwable cause) {
        super("Kartverket kunne ikke levere turdata akkurat nå. Vent litt og prøv igjen.", cause);
    }
}
