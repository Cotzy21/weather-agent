package no.weatheragent.web;

import no.weatheragent.account.AuthUserRemover;
import no.weatheragent.account.UserDataEraser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Kontosletting (GDPR art. 17). Brukeren identifiseres av JWT-en, så en kan bare slette sin egen konto.
 * Først slettes alle appdata i én transaksjon, deretter forsøkes selve innloggingen fjernet hos Supabase.
 */
@RestController
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    /** {@code loginRemoved=false}: dataene er slettet, men innloggingen (e-posten) må fjernes på annen måte. */
    public record AccountDeletionResult(int deletedRows, boolean loginRemoved) {}

    private final UserDataEraser eraser;
    private final AuthUserRemover authUserRemover;

    public AccountController(UserDataEraser eraser, AuthUserRemover authUserRemover) {
        this.eraser = eraser;
        this.authUserRemover = authUserRemover;
    }

    @DeleteMapping("/api/konto")
    public AccountDeletionResult deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        int rows = eraser.eraseAll(userId);
        boolean loginRemoved = authUserRemover.remove(userId);
        log.info("Konto slettet: {} rader fjernet, innlogging fjernet={}", rows, loginRemoved);
        return new AccountDeletionResult(rows, loginRemoved);
    }
}
