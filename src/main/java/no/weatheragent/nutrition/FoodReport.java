package no.weatheragent.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** En brukers rapport om en offentlig delt matvare. Maks én per bruker og vare (håndheves i DB). */
@Entity
@Table(name = "food_reports")
public class FoodReport {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reason", length = 20, nullable = false)
    private String reason;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FoodReport() {
        // for JPA
    }

    public FoodReport(UUID foodId, UUID reporterId, ReportReason reason, String note) {
        this.foodId = foodId;
        this.reporterId = reporterId;
        this.reason = reason.name();
        this.note = note;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFoodId() { return foodId; }
    public UUID getReporterId() { return reporterId; }
    public ReportReason getReason() { return ReportReason.valueOf(reason); }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
