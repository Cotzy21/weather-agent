package no.weatheragent.account;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;

class AuthDeletionRetryTest {
    @Test void retriesOnlyPendingIdentitiesReturnedByTheBoundedQuery() {
        var jdbc = mock(JdbcTemplate.class);
        var remover = mock(AuthUserRemover.class);
        UUID owner = UUID.randomUUID();
        when(jdbc.query(contains("revoked_at is not null and auth_deleted_at is null"), any(RowMapper.class)))
                .thenReturn(List.of(owner));
        new AuthDeletionRetry(jdbc, remover).retryPending();
        verify(remover).remove(owner);
        verifyNoMoreInteractions(remover);
    }
}
