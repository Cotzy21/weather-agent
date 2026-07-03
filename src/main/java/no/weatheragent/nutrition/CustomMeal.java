package no.weatheragent.nutrition;

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
 * Et egendefinert måltid en bruker har satt sammen av favoritt-ingredienser.
 * Ingrediensene (med gram og makroer) lagres som JSONB i formen frontenden
 * bruker - samme fleksible mønster som Workout.content og SavedRoute.geometry.
 */
@Entity
@Table(name = "custom_meals")
public class CustomMeal {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ingredients", columnDefinition = "jsonb", nullable = false)
    private JsonNode ingredients;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomMeal() {
        // for JPA
    }

    public CustomMeal(UUID userId, String name, JsonNode ingredients) {
        this.userId = userId;
        this.name = name;
        this.ingredients = ingredients;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public JsonNode getIngredients() { return ingredients; }
    public Instant getCreatedAt() { return createdAt; }
}
