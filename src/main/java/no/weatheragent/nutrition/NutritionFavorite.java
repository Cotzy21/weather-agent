package no.weatheragent.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * En matvare en bruker har merket som favoritt i Kosthold-fanen. Eies av en
 * Supabase-bruker (userId = JWT-ens sub-claim), samme mønster som SavedRoute.
 * foodKey er "familie:navn" og unik per bruker (håndheves også i DB).
 */
@Entity
@Table(name = "nutrition_favorites")
public class NutritionFavorite {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "food_key", length = 120, nullable = false)
    private String foodKey;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "emoji", length = 16, nullable = false)
    private String emoji;

    @Column(name = "tag", length = 40, nullable = false)
    private String tag;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NutritionFavorite() {
        // for JPA
    }

    public NutritionFavorite(UUID userId, String foodKey, String name, String emoji, String tag) {
        this.userId = userId;
        this.foodKey = foodKey;
        this.name = name;
        this.emoji = emoji == null ? "" : emoji;
        this.tag = tag == null ? "" : tag;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getFoodKey() { return foodKey; }
    public String getName() { return name; }
    public String getEmoji() { return emoji; }
    public String getTag() { return tag; }
    public Instant getCreatedAt() { return createdAt; }
}
