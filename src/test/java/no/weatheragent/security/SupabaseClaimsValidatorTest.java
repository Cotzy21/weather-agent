package no.weatheragent.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupabaseClaimsValidatorTest {

    private final SupabaseClaimsValidator validator = new SupabaseClaimsValidator();

    private Jwt jwt(Consumer<Map<String, Object>> claims) {
        Map<String, Object> c = new java.util.HashMap<>();
        c.put("iss", "test");
        claims.accept(c);
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "HS256"), c);
    }

    @Test
    void acceptsLoggedInUserToken() {
        assertTrue(validator.validate(jwt(c -> {
            c.put("sub", UUID.randomUUID().toString());
            c.put("role", "authenticated");
            c.put("aud", List.of("authenticated"));
        })).getErrors().isEmpty());
    }

    @Test
    void rejectsTheAnonKeyThatIsPublicInTheFrontend() {
        assertFalse(validator.validate(jwt(c -> c.put("role", "anon"))).getErrors().isEmpty());
    }

    @Test
    void rejectsServiceRoleTokens() {
        assertFalse(validator.validate(jwt(c -> {
            c.put("sub", UUID.randomUUID().toString());
            c.put("role", "service_role");
        })).getErrors().isEmpty());
    }

    @Test
    void rejectsTokensWithoutAUserId() {
        assertFalse(validator.validate(jwt(c -> c.put("role", "authenticated"))).getErrors().isEmpty());
        assertFalse(validator.validate(jwt(c -> {
            c.put("sub", "ikke-en-uuid");
            c.put("role", "authenticated");
        })).getErrors().isEmpty());
    }

    @Test
    void audienceAloneIsEnoughWhenTheRoleClaimIsMissing() {
        assertTrue(validator.validate(jwt(c -> {
            c.put("sub", UUID.randomUUID().toString());
            c.put("aud", List.of("authenticated"));
        })).getErrors().isEmpty());
    }
}
