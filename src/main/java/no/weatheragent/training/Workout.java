package no.weatheragent.training;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * En logget treningsøkt for en bruker (userId = Supabase-JWT sub). Inneholder
 * settene som ble gjort. Grunnlaget for progresjon over tid og senere for
 * planer/wearable-tilpasning.
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

    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ExerciseSet> sets = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Workout() {
        // for JPA
    }

    public Workout(UUID userId, LocalDate date, String title) {
        this.userId = userId;
        this.date = date;
        this.title = title;
        this.createdAt = Instant.now();
    }

    /** Legg til et sett, og koble begge sider av relasjonen. */
    public void addSet(String exercise, int reps, double weightKg) {
        sets.add(new ExerciseSet(this, exercise, reps, weightKg, sets.size()));
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public String getTitle() { return title; }
    public List<ExerciseSet> getSets() { return sets; }
    public Instant getCreatedAt() { return createdAt; }
}
