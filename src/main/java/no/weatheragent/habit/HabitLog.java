package no.weatheragent.habit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Én dags logg for en vane: verdien (antall kopper/timer/min - eller 1 for
 * en ja/nei-vane). Maks én rad per vane per dag (håndheves i DB); ny logging
 * samme dag overskriver.
 */
@Entity
@Table(name = "habit_logs")
public class HabitLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "habit_id", nullable = false)
    private UUID habitId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "value", nullable = false)
    private double value;

    protected HabitLog() {
        // for JPA
    }

    public HabitLog(UUID habitId, LocalDate date, double value) {
        this.habitId = habitId;
        this.date = date;
        this.value = value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public UUID getId() { return id; }
    public UUID getHabitId() { return habitId; }
    public LocalDate getDate() { return date; }
    public double getValue() { return value; }
}
