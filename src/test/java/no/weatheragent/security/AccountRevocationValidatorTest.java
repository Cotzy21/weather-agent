package no.weatheragent.security;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountRevocationValidatorTest {
    @Test
    void previouslyValidTokenIsRejectedAfterRevocationWithoutPositiveCaching() {
        var jdbc = mock(JdbcTemplate.class);
        UUID owner = UUID.randomUUID();
        Jwt token = Jwt.withTokenValue("test").header("alg", "HS256").subject(owner.toString()).build();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(owner))).thenReturn(false, true);
        var validator = new AccountRevocationValidator(jdbc);
        assertFalse(validator.validate(token).hasErrors());
        assertTrue(validator.validate(token).hasErrors());
    }

    @Test
    void unavailableDatabaseCannotAllowTheToken() {
        var jdbc = mock(JdbcTemplate.class);
        UUID owner = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(owner))).thenThrow(new IllegalStateException("offline"));
        assertTrue(new AccountRevocationValidator(jdbc).validate(
                Jwt.withTokenValue("test").header("alg", "HS256").subject(owner.toString()).build()).hasErrors());
    }
}
