package no.weatheragent.account;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;

/** Streams a consistent, owner-scoped snapshot without loading the entire account into memory. */
@Service
public class UserDataExporter {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public UserDataExporter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void write(UUID userId, String email, OutputStream output) throws IOException {
        try (JsonGenerator json = mapper.getFactory().createGenerator(output)) {
            json.writeStartObject();
            json.writeNumberField("formatVersion", 1);
            json.writeStringField("exportedAt", Instant.now().toString());
            json.writeStringField("userId", userId.toString());
            json.writeStringField("email", email);
            json.writeObjectFieldStart("data");
            rows(json, "account_access", "select row_to_json(t)::text from revoked_accounts t where user_id = ?", userId);
            rows(json, UserDataEraser.HABIT_LOGS,
                    "select row_to_json(t)::text from habit_logs t where habit_id in (select id from habits where user_id = ?)", userId);
            for (var table : UserDataEraser.OWNED_TABLES) {
                // Identifiers come only from the same fixed registry used by account erasure.
                rows(json, table.table(), "select row_to_json(t)::text from " + table.table()
                        + " t where " + table.ownerColumn() + " = ?", userId);
            }
            json.writeEndObject();
            json.writeEndObject();
        }
    }

    private void rows(JsonGenerator json, String name, String sql, UUID userId) throws IOException {
        json.writeArrayFieldStart(name);
        jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setObject(1, userId);
            statement.setFetchSize(100);
            return statement;
        }, (RowCallbackHandler) row -> {
            try {
                json.writeRawValue(row.getString(1));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        json.writeEndArray();
    }
}
