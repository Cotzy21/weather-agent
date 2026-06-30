package no.weatheragent.routes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import no.weatheragent.route.RoutePoint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * En rute en bruker har lagret. Eies av en Supabase-bruker (userId = JWT-ens
 * sub-claim). Geometrien lagres som JSONB - et konkret eksempel på hvorfor én
 * PostgreSQL holder også for «rotete»/fleksibel data.
 */
@Entity
@Table(name = "saved_routes")
public class SavedRoute {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "distance_km", nullable = false)
    private double distanceKm;

    @Column(name = "ascent_m", nullable = false)
    private double ascentM;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geometry", columnDefinition = "jsonb", nullable = false)
    private List<RoutePoint> geometry;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SavedRoute() {
        // for JPA
    }

    public SavedRoute(UUID userId, String name, double distanceKm, double ascentM, List<RoutePoint> geometry) {
        this.userId = userId;
        this.name = name;
        this.distanceKm = distanceKm;
        this.ascentM = ascentM;
        this.geometry = geometry;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public double getDistanceKm() { return distanceKm; }
    public double getAscentM() { return ascentM; }
    public List<RoutePoint> getGeometry() { return geometry; }
    public Instant getCreatedAt() { return createdAt; }
}
