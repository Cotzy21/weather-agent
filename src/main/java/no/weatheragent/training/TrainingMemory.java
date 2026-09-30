package no.weatheragent.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Brukerens eget treningsminne (én rad per bruker). Lister lagres komma-separert. */
@Entity
@Table(name = "training_memory")
public class TrainingMemory {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "liked", length = 1500, nullable = false)
    private String liked;

    @Column(name = "disliked", length = 1500, nullable = false)
    private String disliked;

    @Column(name = "notes", length = 500, nullable = false)
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingMemory() {
        // for JPA
    }

    public TrainingMemory(UUID userId, List<String> liked, List<String> disliked, String notes) {
        this.userId = userId;
        this.liked = String.join(",", liked);
        this.disliked = String.join(",", disliked);
        this.notes = notes;
        this.updatedAt = Instant.now();
    }

    public List<String> liked() { return split(liked); }
    public List<String> disliked() { return split(disliked); }
    public String notes() { return notes; }
    public UUID getUserId() { return userId; }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
