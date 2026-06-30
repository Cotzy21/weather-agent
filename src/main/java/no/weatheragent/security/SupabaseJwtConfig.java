package no.weatheragent.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Hvordan vi validerer Supabase-tokens. To varianter:
 *  - Eldre prosjekter signerer med HS256 (delt secret) -> sett {@code supabase.jwt-secret}
 *    (env SUPABASE_JWT_SECRET, fra Supabase Settings -> API -> JWT Secret).
 *  - Nyere prosjekter bruker asymmetriske nøkler -> sett {@code SUPABASE_JWKS_URI} i stedet.
 *
 * Secreten er hemmelig (kan signere tokens) - kun via env, aldri i git/logg.
 */
@Configuration
public class SupabaseJwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${supabase.jwt-secret:}") String hs256Secret,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {

        if (hs256Secret != null && !hs256Secret.isBlank()) {
            SecretKeySpec key = new SecretKeySpec(hs256Secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        }
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
}
