package no.weatheragent.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

/**
 * Godtar bare tokens som tilhører en innlogget bruker. Supabase signerer også de offentlige
 * {@code anon}- og {@code service_role}-nøklene med samme hemmelighet, og anon-nøkkelen ligger i
 * frontend-koden. Uten denne sjekken er de gyldige JWT-er (uten brukerid), og de slipper gjennom
 * signaturvalideringen til endepunkter som forventer en bruker-UUID i {@code sub}.
 */
public class SupabaseClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error NOT_A_USER = new OAuth2Error("invalid_token",
            "Tokenet tilhører ikke en innlogget bruker.", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        List<String> audience = jwt.getAudience() == null ? List.of() : jwt.getAudience();
        boolean authenticated = "authenticated".equals(role) || audience.contains("authenticated");
        boolean serviceOrAnon = "anon".equals(role) || "service_role".equals(role);
        if (!authenticated || serviceOrAnon || !isUuid(jwt.getSubject())) {
            return OAuth2TokenValidatorResult.failure(NOT_A_USER);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static boolean isUuid(String s) {
        if (s == null) return false;
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
