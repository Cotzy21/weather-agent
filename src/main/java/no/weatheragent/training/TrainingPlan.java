package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * En lagret treningsplan i brukerens profil - typisk et AI-forslag brukeren
 * likte og vil gjenbruke. Samme fleksible form som {@link Workout} (type +
 * JSONB-innhold), så en plan kan gjøres om til en økt uten oversetting:
 * innholdet ER alt øktskjemaet trenger.
 */
@Entity
@Table(name = "training_plans")
public class TrainingPlan {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", length = 120, nullable = false)
    private String title;

    @Column(name = "type", length = 40, nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private JsonNode content;

    /** AI-ens korte begrunnelse for planen - fin å se igjen senere. */
    @Column(name = "rationale", columnDefinition = "text")
    private String rationale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TrainingPlan() {
        // for JPA
    }

    public TrainingPlan(UUID userId, String title, String type, JsonNode content, String rationale) {
        this.userId = userId;
        this.title = title;
        this.type = type;
        this.content = content;
        this.rationale = rationale;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public JsonNode getContent() { return content; }
    public String getRationale() { return rationale; }
    public Instant getCreatedAt() { return createdAt; }
}
