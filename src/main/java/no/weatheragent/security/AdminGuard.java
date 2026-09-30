package no.weatheragent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hvem er administrator? Bruker-id-ene (Supabase-UUID-er) står i {@code app.admin.user-ids}
 * (miljøvariabelen {@code APP_ADMIN_USER_IDS}, kommaseparert). Tom liste, som er standard, betyr at ingen er
 * administrator og at alle admin-endepunkter svarer 403. Identiteten kommer fra den validerte JWT-en, så den
 * kan ikke forfalskes av klienten.
 */
@Component
public class AdminGuard {

    private static final Logger log = LoggerFactory.getLogger(AdminGuard.class);

    private final Set<UUID> adminIds = new HashSet<>();

    public AdminGuard(@Value("${app.admin.user-ids:}") List<String> configured) {
        for (String raw : configured) {
            String id = raw.trim();
            if (id.isEmpty()) {
                continue;
            }
            try {
                adminIds.add(UUID.fromString(id));
            } catch (IllegalArgumentException e) {
                log.warn("Ignorerer ugyldig bruker-id i app.admin.user-ids (må være en UUID).");
            }
        }
    }

    public boolean isAdmin(Jwt jwt) {
        try {
            return jwt != null && adminIds.contains(UUID.fromString(jwt.getSubject()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Kaster {@link AccessDeniedException} (-> 403) hvis innlogget bruker ikke er administrator. */
    public void require(Jwt jwt) {
        if (!isAdmin(jwt)) {
            throw new AccessDeniedException("Bare administratorer har tilgang.");
        }
    }
}
