package no.weatheragent.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Én logget matvare i kostholdsdagboka: bruker + dato + måltid + mengde i gram.
 *
 * Kalorier og makroer LAGRES som et øyeblikksbilde ved logging (utregnet fra
 * Matvaretabellen), så historikken står seg selv om tabellen endres. Vitaminer/
 * mineraler lagres IKKE per rad - de beregnes ved lesing via foodId mot den
 * cachede tabellen (mange kolonner spart, og tallene er ferskvare uansett).
 */
@Entity
@Table(name = "meal_entries")
public class MealEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    /** FROKOST, LUNSJ, MIDDAG, KVELDS eller MELLOM. */
    @Column(name = "meal", length = 20, nullable = false)
    private String meal;

    @Column(name = "food_id", length = 20, nullable = false)
    private String foodId;

    @Column(name = "food_name", length = 160, nullable = false)
    private String foodName;

    @Column(name = "grams", nullable = false)
    private double grams;

    @Column(name = "kcal", nullable = false)
    private double kcal;

    @Column(name = "protein_g", nullable = false)
    private double proteinG;

    @Column(name = "fat_g", nullable = false)
    private double fatG;

    @Column(name = "carb_g", nullable = false)
    private double carbG;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MealEntry() {
        // for JPA
    }

    public MealEntry(UUID userId, LocalDate date, String meal, String foodId, String foodName,
                     double grams, double kcal, double proteinG, double fatG, double carbG) {
        this.userId = userId;
        this.date = date;
        this.meal = meal;
        this.foodId = foodId;
        this.foodName = foodName;
        this.grams = grams;
        this.kcal = kcal;
        this.proteinG = proteinG;
        this.fatG = fatG;
        this.carbG = carbG;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getDate() { return date; }
    public String getMeal() { return meal; }
    public String getFoodId() { return foodId; }
    public String getFoodName() { return foodName; }
    public double getGrams() { return grams; }
    public double getKcal() { return kcal; }
    public double getProteinG() { return proteinG; }
    public double getFatG() { return fatG; }
    public double getCarbG() { return carbG; }
    public Instant getCreatedAt() { return createdAt; }
}
