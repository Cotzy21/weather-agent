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
import java.time.LocalDate;
import java.util.UUID;

/**
 * En logget treningsøkt for en bruker (userId = Supabase-JWT sub). Fleksibel:
 * {@code type} sier hva slags økt det er (STYRKE, LØPING, SVØMMING, BULDRING,
 * HIKING, SYKKEL, FRISTIL ...), og {@code content} er JSONB med de typespesifikke
 * detaljene (sett/dropsett/supersett for styrke, distanse/tid for kondisjon osv.).
 * JSONB lar UI-et definere formen uten at vi trenger en tabell per variant.
 */
@Entity
@Table(name = "workouts")
public class Workout {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "title", length = 120, nullable = false)
    private String title;

    @Column(name = "type", length = 40, nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "jsonb", nullable = false)
    private JsonNode content;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Workout() {
        // for JPA
    }

    public Workout(UUID userId, LocalDate date, String title, String type, JsonNode content, String notes) {
        this.userId = userId;
        this.date = date;
        this.title = title;
        this.type = type;
        this.content = content;
        this.notes = notes;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public JsonNode getContent() { return content; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}
