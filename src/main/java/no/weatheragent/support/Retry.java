package no.weatheragent.support;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.function.Supplier;

/**
 * Liten retry-hjelper for FLYKTIGE nettverksfeil. Eksterne API-er som Overpass
 * svarer av og til 504 "server too busy"; et nytt forsøk etter en kort pause
 * går som regel bra.
 *
 * Vi prøver bare på nytt ved:
 *  - 5xx fra serveren ({@link HttpServerErrorException}), og
 *  - timeout/connection-feil ({@link ResourceAccessException}).
 * 4xx (våre egne feil, f.eks. ugyldig spørring) prøves IKKE på nytt - da er
 * et nytt forsøk meningsløst.
 */
public final class Retry {

    private Retry() {
    }

    /**
     * Kjør {@code call}, og prøv på nytt ved flyktige feil inntil {@code maxAttempts}
     * totalt. Pausen mellom forsøk øker lineært ({@code backoffMillis} * forsøksnr).
     * Kaster den siste feilen hvis alle forsøk feiler.
     */
    public static <T> T withRetry(int maxAttempts, long backoffMillis, Supplier<T> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                last = e;
                if (attempt < maxAttempts) {
                    sleep(backoffMillis * attempt);
                }
            }
        }
        throw last;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Avbrutt under retry-pause", e);
        }
    }
}
