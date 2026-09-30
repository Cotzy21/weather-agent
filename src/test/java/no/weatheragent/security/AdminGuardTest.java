package no.weatheragent.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminGuardTest {

    private static final String ADMIN = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String USER = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    private static Jwt jwtFor(String subject) {
        return Jwt.withTokenValue("t").header("alg", "none").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void onlyConfiguredUserIdsAreAdmins() {
        AdminGuard guard = new AdminGuard(List.of(ADMIN));

        assertTrue(guard.isAdmin(jwtFor(ADMIN)));
        assertFalse(guard.isAdmin(jwtFor(USER)));
        assertDoesNotThrow(() -> guard.require(jwtFor(ADMIN)));
        assertThrows(AccessDeniedException.class, () -> guard.require(jwtFor(USER)));
    }

    @Test
    void nobodyIsAdminByDefault() {
        AdminGuard guard = new AdminGuard(List.of());

        assertFalse(guard.isAdmin(jwtFor(ADMIN)));
        assertThrows(AccessDeniedException.class, () -> guard.require(jwtFor(ADMIN)));
    }

    @Test
    void whitespaceAndBrokenEntriesAreIgnoredWithoutMakingAnyoneAdmin() {
        AdminGuard guard = new AdminGuard(List.of("  ", "", "ikke-en-uuid", " " + ADMIN + " "));

        assertTrue(guard.isAdmin(jwtFor(ADMIN)));
        assertFalse(guard.isAdmin(jwtFor(USER)));
    }

    @Test
    void aTokenWithoutAValidUuidSubjectIsNeverAdmin() {
        AdminGuard guard = new AdminGuard(List.of(ADMIN));

        assertFalse(guard.isAdmin(jwtFor("admin")));
        assertFalse(guard.isAdmin(null));
    }
}
