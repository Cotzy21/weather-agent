package no.weatheragent.account;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class TrainingAiPermission {
    private static final int VERSION = 1;
    private static final String KEY = "privacy-training-ai";
    private final JdbcTemplate jdbc;

    public TrainingAiPermission(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Decision(int version, boolean allowed) {}

    public Decision get(UUID owner) {
        boolean allowed = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from user_settings where user_id = ? and key = ?
                  and value -> 'version' = to_jsonb(?::int) and value -> 'allowed' = 'true'::jsonb)
                """, Boolean.class, owner, KEY, VERSION));
        return new Decision(VERSION, allowed);
    }

    public Decision record(UUID owner, int version, boolean allowed) {
        if (version != VERSION) throw new ResponseStatusException(HttpStatus.CONFLICT);
        // Preserve the previous grant timestamp when the user withdraws permission.
        jdbc.update("""
                insert into user_settings (user_id, key, value, updated_at)
                values (?, ?, jsonb_build_object('version', ?::int, 'allowed', ?::boolean,
                        ?::text, current_timestamp), current_timestamp)
                on conflict (user_id, key) do update set
                  value = case when user_settings.value -> 'version' = excluded.value -> 'version'
                          then user_settings.value || excluded.value else excluded.value end,
                  updated_at = excluded.updated_at
                where user_settings.value -> 'version' is distinct from excluded.value -> 'version'
                   or user_settings.value -> 'allowed' is distinct from excluded.value -> 'allowed'
                """, owner, KEY, VERSION, allowed, allowed ? "grantedAt" : "withdrawnAt");
        return new Decision(VERSION, allowed);
    }

    public void require(UUID owner, String language) {
        if (!get(owner).allowed()) {
            boolean english = "en".equalsIgnoreCase(language);
            throw new ConsentRequired(english ? "Enable Training AI to send your request."
                    : "Slå på trenings-AI for å sende forespørselen.");
        }
    }

    public static class ConsentRequired extends RuntimeException {
        ConsentRequired(String message) { super(message); }
    }
}
