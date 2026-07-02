package no.weatheragent.web;

import no.weatheragent.nutrition.FavoriteLimitException;
import no.weatheragent.training.AiSuggestionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gjør feil fra de eksterne tjenestene (Overpass/MET) om til en ryddig JSON-feil
 * med en forståelig norsk melding, i stedet for en rå 500-side. Frontend leser
 * {@code error}-feltet og viser det.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String error) {
    }

    @ExceptionHandler({RestClientResponseException.class, ResourceAccessException.class})
    public ResponseEntity<ApiError> handleUpstream(Exception e) {
        boolean rateLimited = e instanceof RestClientResponseException r
                && r.getStatusCode().value() == 429;

        String message = rateLimited
                ? "Kartdata-tjenesten (OpenStreetMap) er midlertidig overbelastet. Vent et minutt og prøv igjen."
                : "En ekstern datatjeneste (vær eller kart) er utilgjengelig akkurat nå. Prøv igjen om litt.";

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(message));
    }

    @ExceptionHandler(AiSuggestionException.class)
    public ResponseEntity<ApiError> handleAi(AiSuggestionException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(e.getMessage()));
    }

    /** Tak på antall favoritter nådd -> 409 med en forståelig melding. */
    @ExceptionHandler(FavoriteLimitException.class)
    public ResponseEntity<ApiError> handleFavoriteLimit(FavoriteLimitException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.getMessage()));
    }
}
