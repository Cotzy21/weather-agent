package no.weatheragent.habit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * En vane brukeren følger med på (koffein, søvn, lesing, meditasjon …).
 * {@code unit} avgjør formen: satt («kopper», «timer», «min») betyr at man
 * logger en MENGDE per dag; tom betyr en ja/nei-vane («gjort i dag?»).
 */
@Entity
@Table(name = "habits")
public class Habit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", length = 40, nullable = false)
    private String name;

    @Column(name = "emoji", length = 16, nullable = false)
    private String emoji;

    @Column(name = "unit", length = 20, nullable = false)
    private String unit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Habit() {
        // for JPA
    }

    public Habit(UUID userId, String name, String emoji, String unit) {
        this.userId = userId;
        this.name = name;
        this.emoji = emoji == null ? "" : emoji;
        this.unit = unit == null ? "" : unit;
        this.createdAt = Instant.now();
    }

    /** Ja/nei-vane (ingen enhet) eller mengde-vane? */
    public boolean isBoolean() {
        return unit.isBlank();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmoji() { return emoji; }
    public String getUnit() { return unit; }
    public Instant getCreatedAt() { return createdAt; }
}
