package no.weatheragent.training;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Svaret på «hvilken av appens øvelser er dette navnet?» for én bruker; {@code exerciseId} er null = ingen treff. */
@Entity
@Table(name = "exercise_name_map")
public class ExerciseNameMapping {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "user_id")
        private UUID userId;
        @Column(name = "name_key", length = 120)
        private String nameKey;

        protected Key() {
        }

        public Key(UUID userId, String nameKey) {
            this.userId = userId;
            this.nameKey = nameKey;
        }

        public UUID userId() { return userId; }
        public String nameKey() { return nameKey; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(nameKey, k.nameKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, nameKey);
        }
    }

    @EmbeddedId
    private Key id;

    @Column(name = "exercise_id", length = 60)
    private String exerciseId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExerciseNameMapping() {
    }

    public ExerciseNameMapping(UUID userId, String nameKey, String exerciseId) {
        this.id = new Key(userId, nameKey);
        this.exerciseId = exerciseId;
        this.createdAt = Instant.now();
    }

    public String nameKey() { return id.nameKey(); }
    public String exerciseId() { return exerciseId; }
}
