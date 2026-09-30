package no.weatheragent.body;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Én innveiing. Maks én per bruker per dag (håndheves i DB); ny logging samme dag overskriver. */
@Entity
@Table(name = "weigh_ins")
public class WeighIn {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WeighIn() {
        // for JPA
    }

    public WeighIn(UUID userId, LocalDate date, double weightKg) {
        this.userId = userId;
        this.date = date;
        this.weightKg = weightKg;
        this.createdAt = Instant.now();
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public double getWeightKg() { return weightKg; }
    public Instant getCreatedAt() { return createdAt; }
}
