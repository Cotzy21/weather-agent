package no.weatheragent.web;

import no.weatheragent.nutrition.FavoriteLimitException;
import no.weatheragent.nutrition.InvalidMealException;
import no.weatheragent.nutrition.MealLimitException;
import no.weatheragent.nutrition.UnknownFoodException;
import no.weatheragent.training.AiSuggestionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
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

    /** AI-kvoten per bruker er brukt opp -> 429 med Retry-After. */
    @ExceptionHandler(no.weatheragent.training.AiRateLimiter.LimitExceededException.class)
    public ResponseEntity<ApiError> handleAiLimit(no.weatheragent.training.AiRateLimiter.LimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.retryAfterSeconds()))
                .body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(AiSuggestionException.class)
    public ResponseEntity<ApiError> handleAi(AiSuggestionException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(e.getMessage()));
    }

    /** Tak på antall favoritter/måltider nådd -> 409 med en forståelig melding. */
    @ExceptionHandler({FavoriteLimitException.class, MealLimitException.class})
    public ResponseEntity<ApiError> handleLimit(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.getMessage()));
    }

    /** Ugyldig måltid fra klienten (tom/for stor ingrediensliste) -> 400. */
    @ExceptionHandler(InvalidMealException.class)
    public ResponseEntity<ApiError> handleInvalidMeal(InvalidMealException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
    }

    /** Logging mot en matvare som ikke finnes (utdatert id) -> 404. */
    @ExceptionHandler(UnknownFoodException.class)
    public ResponseEntity<ApiError> handleUnknownFood(UnknownFoodException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
    }

    /** Ugyldige verdier fra klienten (f.eks. ukjent måltid) -> 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadInput(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
    }

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Uleselig JSON eller feil type på et felt -> 400 uten å ekko parser-detaljer. */
    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("Ugyldig forespørsel."));
    }

    /**
     * Alt uventet -> generisk 500. Detaljer (SQL, stier, klassenavn, hemmeligheter i feilmeldinger)
     * havner i loggen, aldri i svaret. Spring sine egne feil (404, 405, 415 ...) beholder sin statuskode,
     * og sikkerhetsfeil slippes videre til Spring Security.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        if (e instanceof ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode()).body(new ApiError("Ugyldig forespørsel."));
        }
        log.error("Uventet feil", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Noe gikk galt hos oss. Prøv igjen om litt."));
    }
}
