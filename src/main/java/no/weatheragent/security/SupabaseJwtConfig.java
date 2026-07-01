package no.weatheragent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Validerer Supabase-tokens. Supabase kan signere brukertokens med HS256 (delt
 * secret) ELLER med asymmetriske nøkler (JWKS) - og hvilken det er, ser man ikke
 * på anon-nøkkelen. Vi konfigurerer derfor begge når de er satt, og velger riktig
 * dekoder ut fra tokenets {@code alg}-header.
 *
 * Sett gjerne BEGGE:
 *   SUPABASE_JWT_SECRET  (Settings -> API -> JWT Secret)
 *   SUPABASE_JWKS_URI    (https://<ref>.supabase.co/auth/v1/.well-known/jwks.json)
 * Hemmeligheter kun via env, aldri i git/logg.
 */
@Configuration
public class SupabaseJwtConfig {

    private static final Logger log = LoggerFactory.getLogger(SupabaseJwtConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${supabase.jwt-secret:}") String secret,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {

        JwtDecoder hs256 = null;
        JwtDecoder jwks = null;

        if (secret != null && !secret.isBlank()) {
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hs256 = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        }
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            jwks = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }

        log.info("Supabase JWT-validering konfigurert: HS256={}, JWKS={}", hs256 != null, jwks != null);

        if (hs256 != null && jwks != null) {
            JwtDecoder hmac = hs256;
            JwtDecoder asym = jwks;
            return token -> ("HS256".equalsIgnoreCase(algOf(token)) ? hmac : asym).decode(token);
        }
        if (hs256 != null) {
            return hs256;
        }
        if (jwks != null) {
            return jwks;
        }
        log.warn("Verken SUPABASE_JWT_SECRET eller SUPABASE_JWKS_URI er satt - innlogging vil gi 401.");
        return NimbusJwtDecoder.withJwkSetUri("").build();
    }

    /** Leser {@code alg} fra JWT-headeren (første segment) uten å validere. */
    private static String algOf(String token) {
        try {
            String headerSegment = token.split("\\.")[0];
            byte[] json = Base64.getUrlDecoder().decode(headerSegment);
            return MAPPER.readTree(json).path("alg").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
