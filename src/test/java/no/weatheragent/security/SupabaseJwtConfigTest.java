package no.weatheragent.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SupabaseJwtConfigTest {
    @Test
    void issuerComesFromTrustedConfigurationNotTheToken() {
        assertEquals("https://project.supabase.co/auth/v1", SupabaseJwtConfig.expectedIssuer("",
                "https://project.supabase.co/auth/v1/.well-known/jwks.json"));
        assertEquals("", SupabaseJwtConfig.expectedIssuer("", ""));
        assertThrows(IllegalArgumentException.class, () -> SupabaseJwtConfig.expectedIssuer("http://example.test", ""));
    }

    @Test
    void rejectsWrongIssuerAndExpiredTokens() {
        var validator = SupabaseJwtConfig.validators("https://project.supabase.co/auth/v1");
        assertFalse(validator.validate(token("https://project.supabase.co/auth/v1", 300)).hasErrors());
        assertTrue(validator.validate(token("https://attacker.test/auth/v1", 300)).hasErrors());
        assertTrue(validator.validate(token("https://project.supabase.co/auth/v1", -300)).hasErrors());
    }

    private Jwt token(String issuer, long expiry) {
        return Jwt.withTokenValue("test").header("alg", "HS256").subject(UUID.randomUUID().toString())
                .issuer(issuer).claim("role", "authenticated").audience(List.of("authenticated"))
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().plusSeconds(expiry)).build();
    }
}
