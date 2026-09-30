package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** En innstilling per (bruker, nøkkel) med fri JSON-verdi. Brukes til dashboard-oppsett og treningsprogram. */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "user_id")
        private UUID userId;
        @Column(name = "key", length = 40)
        private String key;

        protected Key() {
        }

        public Key(UUID userId, String key) {
            this.userId = userId;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(key, k.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, key);
        }
    }

    @EmbeddedId
    private Key id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", columnDefinition = "jsonb", nullable = false)
    private JsonNode value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSettings() {
    }

    public UserSettings(UUID userId, String key, JsonNode value) {
        this.id = new Key(userId, key);
        this.value = value;
        this.updatedAt = Instant.now();
    }

    public JsonNode getValue() { return value; }
}
