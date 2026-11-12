package no.weatheragent.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;

/** No positive cache: a completed deletion must invalidate previously issued access tokens. */
public class AccountRevocationValidator implements OAuth2TokenValidator<Jwt> {
    private final JdbcTemplate jdbc;

    public AccountRevocationValidator(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        try {
            UUID id = UUID.fromString(jwt.getSubject());
            Boolean revoked = jdbc.queryForObject(
                    "select exists(select 1 from revoked_accounts where user_id = ? and revoked_at is not null)", Boolean.class, id);
            if (Boolean.FALSE.equals(revoked)) return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException ignored) {
            // Invalid subjects and unavailable revocation storage must fail closed.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Kontoen er ikke tilgjengelig.", null));
    }
}
