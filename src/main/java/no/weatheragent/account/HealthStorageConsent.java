package no.weatheragent.account;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

/** Keeps the optional health purpose separate from account creation and Training AI. */
@Service
public class HealthStorageConsent {
    public static final int VERSION = 1;
    private static final Set<String> NON_HEALTH_TABLES = Set.of("saved_routes", "food_reports", "user_settings");
    private final JdbcTemplate jdbc;

    public HealthStorageConsent(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Decision(int version, boolean allowed, boolean withdrawn, String lastWithdrawal) {}

    public Decision get(UUID owner) {
        return jdbc.query("""
                select granted_at is not null and withdrawn_at is null and notice_version = ?,
                       withdrawn_at is not null, last_withdrawn_at::text from health_storage_consent where user_id = ?
                """, (rs, n) -> new Decision(VERSION, rs.getBoolean(1), rs.getBoolean(2), rs.getString(3)), VERSION, owner)
                .stream().findFirst().orElse(new Decision(VERSION, false, false, null));
    }

    @Transactional
    public Decision grant(UUID owner, int version) {
        if (version != VERSION) throw new ResponseStatusException(HttpStatus.CONFLICT);
        lockActiveAccount(owner);
        jdbc.update("""
                insert into health_storage_consent(user_id, notice_version, granted_at)
                values (?, ?, clock_timestamp())
                on conflict (user_id) do update set notice_version = excluded.notice_version,
                  granted_at = excluded.granted_at, withdrawn_at = null
                where health_storage_consent.withdrawn_at is not null
                   or health_storage_consent.notice_version <> excluded.notice_version
                   or health_storage_consent.granted_at is null
                """, owner, VERSION);
        return get(owner);
    }

    @Transactional
    public Decision withdrawAndErase(UUID owner) {
        lockActiveAccount(owner);
        // Force transactions with an older REPEATABLE READ snapshot to retry their account lock.
        jdbc.update("update revoked_accounts set user_id = user_id where user_id = ?", owner);
        jdbc.update("""
                insert into health_storage_consent(user_id, notice_version, withdrawn_at, last_withdrawn_at)
                values (?, ?, current_timestamp, current_timestamp)
                on conflict (user_id) do update set withdrawn_at = excluded.withdrawn_at,
                    last_withdrawn_at = excluded.last_withdrawn_at
                where health_storage_consent.withdrawn_at is null
                """, owner, VERSION);
        jdbc.update("delete from habit_logs where habit_id in (select id from habits where user_id = ?)", owner);
        for (var table : UserDataEraser.OWNED_TABLES) {
            if (!NON_HEALTH_TABLES.contains(table.table()) && !table.table().equals("health_storage_consent")) {
                jdbc.update("delete from " + table.table() + " where " + table.ownerColumn() + " = ?", owner);
            }
        }
        jdbc.update("delete from user_settings where user_id = ? and key in ('program','streak','privacy-training-ai')", owner);
        return get(owner);
    }

    private void lockActiveAccount(UUID owner) {
        jdbc.update("insert into revoked_accounts(user_id) values (?) on conflict (user_id) do nothing", owner);
        Boolean open = jdbc.queryForObject("select revoked_at is null from revoked_accounts where user_id = ? for update",
                Boolean.class, owner);
        if (!Boolean.TRUE.equals(open)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
