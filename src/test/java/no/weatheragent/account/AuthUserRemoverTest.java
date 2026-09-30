package no.weatheragent.account;

import org.junit.jupiter.api.Test;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthUserRemoverTest {

    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AuthUserRemover remover = new AuthUserRemover(jdbc);

    @Test
    void deletesTheSupabaseLoginWhenTheAuthSchemaExists() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(jdbc.update("delete from auth.users where id = ?", USER)).thenReturn(1);

        assertTrue(remover.remove(USER));
    }

    @Test
    void doesNothingOnADatabaseWithoutSupabase() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(false);

        assertFalse(remover.remove(USER));
        verify(jdbc, never()).update(anyString(), eq(USER));
    }

    @Test
    void reportsFalseWhenNoLoginRowWasFound() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(jdbc.update("delete from auth.users where id = ?", USER)).thenReturn(0);

        assertFalse(remover.remove(USER));
    }

    @Test
    void aFailureNeverPropagatesBecauseTheUserDataIsAlreadyGone() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        when(jdbc.update(anyString(), eq(USER))).thenThrow(new PermissionDeniedDataAccessException("nei", null));

        assertFalse(remover.remove(USER));
    }
}
