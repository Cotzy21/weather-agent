package no.weatheragent.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/** Ett sett i en treningsøkt: øvelse, reps og vekt. Hører til én {@link Workout}. */
@Entity
@Table(name = "exercise_sets")
public class ExerciseSet {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;

    @Column(name = "exercise", length = 120, nullable = false)
    private String exercise;

    @Column(name = "reps", nullable = false)
    private int reps;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    @Column(name = "position", nullable = false)
    private int position;

    protected ExerciseSet() {
        // for JPA
    }

    public ExerciseSet(Workout workout, String exercise, int reps, double weightKg, int position) {
        this.workout = workout;
        this.exercise = exercise;
        this.reps = reps;
        this.weightKg = weightKg;
        this.position = position;
    }

    public UUID getId() { return id; }
    public String getExercise() { return exercise; }
    public int getReps() { return reps; }
    public double getWeightKg() { return weightKg; }
    public int getPosition() { return position; }
}
