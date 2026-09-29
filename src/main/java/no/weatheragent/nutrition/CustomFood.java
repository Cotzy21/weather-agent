package no.weatheragent.nutrition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * En matvare brukeren har lagt inn selv - for varer Matvaretabellen ikke har
 * (bestemte merkevarer, proteinshakes …). Næring per 100 g, valgfri porsjon,
 * valgfri strekkode. {@code isPublic} betyr at alle brukere kan søke den opp.
 *
 * I resten av kostholdskoden opptrer den som en vanlig {@link FoodItem} med
 * id {@code egen:<uuid>}, så dagboka logger den akkurat som tabellvarer.
 */
@Entity
@Table(name = "custom_foods")
public class CustomFood {

    /** Prefiks som skiller egne matvarer fra Matvaretabellens id-er ("05.049"). */
    public static final String ID_PREFIX = "egen:";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "brand", length = 80)
    private String brand;

    @Column(name = "barcode", length = 32)
    private String barcode;

    @Column(name = "kcal_per_100g", nullable = false)
    private double kcalPer100g;

    @Column(name = "protein_g", nullable = false)
    private double proteinG;

    @Column(name = "fat_g", nullable = false)
    private double fatG;

    @Column(name = "carb_g", nullable = false)
    private double carbG;

    @Column(name = "portion_name", length = 40)
    private String portionName;

    @Column(name = "portion_grams")
    private Double portionGrams;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomFood() {
        // for JPA
    }

    public CustomFood(UUID ownerId, String name, String brand, String barcode,
                      double kcalPer100g, double proteinG, double fatG, double carbG,
                      String portionName, Double portionGrams, boolean isPublic) {
        this.ownerId = ownerId;
        this.name = name;
        this.brand = brand;
        this.barcode = barcode;
        this.kcalPer100g = kcalPer100g;
        this.proteinG = proteinG;
        this.fatG = fatG;
        this.carbG = carbG;
        this.portionName = portionName;
        this.portionGrams = portionGrams;
        this.isPublic = isPublic;
        this.createdAt = Instant.now();
    }

    /** Som en vanlig matvare, så søk/logging/dagsbalanse ikke trenger å vite forskjellen. */
    public FoodItem toFoodItem() {
        String displayName = brand == null || brand.isBlank() ? name : name + " (" + brand + ")";
        List<FoodItem.Portion> portions = portionName != null && portionGrams != null && portionGrams > 0
                ? List.of(new FoodItem.Portion(portionName, "stk", portionGrams))
                : List.of();
        return new FoodItem(ID_PREFIX + id, displayName, kcalPer100g,
                Map.of("Protein", proteinG, "Fett", fatG, "Karbo", carbG),
                portions, List.of());
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getBarcode() { return barcode; }
    public double getKcalPer100g() { return kcalPer100g; }
    public double getProteinG() { return proteinG; }
    public double getFatG() { return fatG; }
    public double getCarbG() { return carbG; }
    public String getPortionName() { return portionName; }
    public Double getPortionGrams() { return portionGrams; }
    public boolean isPublic() { return isPublic; }
    public Instant getCreatedAt() { return createdAt; }
}
