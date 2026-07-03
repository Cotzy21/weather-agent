package no.weatheragent.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Brukerens kalorimål-profil: det som trengs for å regne ut anbefalt daglig
 * inntak. ÉN rad per bruker (userId er primærnøkkel) - nye lagringer
 * overskriver. Selve målet lagres IKKE; det beregnes alltid ferskt i
 * {@link CalorieGoalService}, så en formel-forbedring slår inn for alle.
 */
@Entity
@Table(name = "calorie_goals")
public class CalorieGoal {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "weight_kg", nullable = false)
    private double weightKg;

    @Column(name = "height_cm", nullable = false)
    private double heightCm;

    @Column(name = "age", nullable = false)
    private int age;

    /** "M" eller "K" - påvirker BMR-formelen. */
    @Column(name = "sex", length = 1, nullable = false)
    private String sex;

    /** ROLIG, LETT, MODERAT eller HØY - hverdagsaktivitet utenom loggede økter. */
    @Column(name = "activity_level", length = 20, nullable = false)
    private String activityLevel;

    /** Ønsket vektendring i kg per uke: negativ = ned, 0 = vedlikehold, positiv = opp. */
    @Column(name = "goal_kg_per_week", nullable = false)
    private double goalKgPerWeek;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CalorieGoal() {
        // for JPA
    }

    public CalorieGoal(UUID userId, double weightKg, double heightCm, int age,
                       String sex, String activityLevel, double goalKgPerWeek) {
        this.userId = userId;
        this.weightKg = weightKg;
        this.heightCm = heightCm;
        this.age = age;
        this.sex = sex;
        this.activityLevel = activityLevel;
        this.goalKgPerWeek = goalKgPerWeek;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public double getWeightKg() { return weightKg; }
    public double getHeightCm() { return heightCm; }
    public int getAge() { return age; }
    public String getSex() { return sex; }
    public String getActivityLevel() { return activityLevel; }
    public double getGoalKgPerWeek() { return goalKgPerWeek; }
    public Instant getUpdatedAt() { return updatedAt; }
}
