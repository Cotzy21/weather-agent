package no.weatheragent.account;

import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

/** Durable bounded retry for identities whose account-data deletion already committed. */
@Configuration
@EnableScheduling
public class AuthDeletionRetry {
    private static final Logger log = LoggerFactory.getLogger(AuthDeletionRetry.class);
    private final JdbcTemplate jdbc;
    private final AuthUserRemover remover;

    public AuthDeletionRetry(JdbcTemplate jdbc, AuthUserRemover remover) {
        this.jdbc = jdbc;
        this.remover = remover;
    }

    @Scheduled(initialDelayString = "${account.deletion-retry.initial-delay-ms:60000}",
            fixedDelayString = "${account.deletion-retry.delay-ms:600000}")
    public void retryPending() {
        try {
            // Rotate failed attempts behind untouched accounts, including across process restarts.
            // Claim a bounded batch atomically so concurrent instances do not select the same batch.
            var owners = jdbc.query("with pending as (select user_id from revoked_accounts "
                    + "where revoked_at is not null and auth_deleted_at is null "
                    + "and (auth_delete_attempted_at is null or auth_delete_attempted_at < current_timestamp - interval '1 minute') "
                    + "order by auth_delete_attempted_at nulls first, revoked_at, user_id limit 20 for update skip locked) "
                    + "update revoked_accounts r set auth_delete_attempted_at = current_timestamp "
                    + "from pending p where r.user_id = p.user_id returning r.user_id",
                    (rs, row) -> rs.getObject(1, UUID.class));
            for (UUID owner : owners) remover.remove(owner);
        } catch (org.springframework.dao.DataAccessException e) {
            log.warn("Account deletion retry unavailable ({})", e.getClass().getSimpleName());
        }
    }
}
