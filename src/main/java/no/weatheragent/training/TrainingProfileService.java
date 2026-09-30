package no.weatheragent.training;

import no.weatheragent.interpret.LlmTier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Onboarding-profilen: erfaring, mål, utstyr, skader og styrker/svakheter. Gir AI-en
 * kontekst i hvert kall, og avgjør hvilket modellnivå brukeren får: nybegynnere og
 * brukere med skader eller usikker teknikk får den sterkeste modellen, fordi en dårlig
 * plan koster dem mest og de ikke selv kan oppdage feilene.
 */
@Service
public class TrainingProfileService {

    static final Set<String> LEVELS = Set.of("BEGINNER", "NOVICE", "INTERMEDIATE", "ADVANCED");
    static final Set<String> GOALS = Set.of("STRENGTH", "MUSCLE", "FAT_LOSS", "ENDURANCE", "HEALTH");
    static final Set<String> EQUIPMENT = Set.of("GYM", "HOME_WEIGHTS", "BODYWEIGHT");
    static final Set<String> TECHNIQUE = Set.of("LOW", "MEDIUM", "HIGH");
    static final Set<String> STYLES = Set.of("DETAILED", "BRIEF");

    static final int MAX_INJURIES = 300;
    static final int MAX_SHORT_TEXT = 200;

    private final TrainingProfileRepository profiles;

    public TrainingProfileService(TrainingProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public Optional<TrainingProfileData> find(UUID userId) {
        return profiles.findById(userId).map(TrainingProfile::data);
    }

    @Transactional
    public TrainingProfileData save(UUID userId, TrainingProfileData in) {
        TrainingProfileData clean = validate(in);
        profiles.save(new TrainingProfile(userId, clean));
        return clean;
    }

    /**
     * Modellnivå for AI-forslag. Uten profil (ikke onboardet) beholdes standarden SMART.
     * Nybegynner/novise, usikker teknikk eller skader => PRO. Ellers SMART.
     */
    @Transactional(readOnly = true)
    public LlmTier tierFor(UUID userId) {
        return find(userId).map(TrainingProfileService::tierOf).orElse(LlmTier.SMART);
    }

    static LlmTier tierOf(TrainingProfileData p) {
        boolean needsCare = "BEGINNER".equals(p.experienceLevel()) || "NOVICE".equals(p.experienceLevel())
                || "LOW".equals(p.technique()) || !p.injuries().isBlank();
        return needsCare ? LlmTier.PRO : LlmTier.SMART;
    }

    /** Tekst til AI-prompten (tom uten profil): fakta om brukeren og hva det betyr for planen. */
    @Transactional(readOnly = true)
    public String promptContext(UUID userId) {
        return find(userId).map(TrainingProfileService::describe).orElse("");
    }

    static String describe(TrainingProfileData p) {
        List<String> lines = new ArrayList<>();
        lines.add("- Erfaringsnivå: " + levelText(p.experienceLevel()) + ", har trent i ca. "
                + months(p.trainingMonths()) + "; tar " + p.sessionsPerWeek() + " økter/uke det siste kvartalet.");
        lines.add("- Mål: " + goalText(p.goal()) + ". Utstyr: " + equipmentText(p.equipment()) + ".");
        lines.add("- Vil trene " + p.daysPerWeek() + " dager i uka, ca. " + p.sessionMinutes() + " min per økt.");
        lines.add("- Teknikk i store løft: " + techniqueText(p.technique()) + ".");
        if (!p.injuries().isBlank()) lines.add("- Skader/begrensninger: " + p.injuries());
        if (!p.background().isBlank()) lines.add("- Bakgrunn: " + p.background());
        if (!p.strengths().isBlank()) lines.add("- Styrker: " + p.strengths());
        if (!p.weaknesses().isBlank()) lines.add("- Svakheter/prioriteres: " + p.weaknesses());
        lines.add("- Foretrekker " + ("DETAILED".equals(p.explanationStyle())
                ? "utdypende forklaringer" : "korte, konkrete begrunnelser") + ".");
        return "\nTreningsprofil (fra onboarding):\n" + String.join("\n", lines) + "\n" + guidance(p);
    }

    /** Konkrete regler som følger av nivået, så planen passer den som skal gjennomføre den. */
    static String guidance(TrainingProfileData p) {
        return switch (p.experienceLevel()) {
            case "BEGINNER", "NOVICE" -> """
                    Tilpasning til nybegynner (VIKTIG):
                      - Enkle, grunnleggende øvelser (maskiner, manualer, goblet-knebøy, kroppsvekt) - ikke
                        teknisk krevende løft som markløft med stang, rykk eller stø-tunge baseløft, med mindre
                        brukeren selv sier de kan teknikken.
                      - Helkropps- eller enkel over-/underkroppsplan, 2-3 økter i uka, med hviledager imellom.
                      - Lavt volum: 2-3 sett per øvelse, 8-15 reps, 4-6 øvelser per økt. Konservative vekter
                        (stopp 2-3 reps før maks) og små steg; «weightKg» kan være lav eller 0 hvis usikker.
                      - Nevn i begrunnelsen kort HVA øvelsen trener og ETT teknikktips. Ikke bruk fagsjargong
                        uten forklaring.""";
            case "INTERMEDIATE" -> """
                    Tilpasning til middels erfaren: bruk delt program (f.eks. over-/underkropp eller PPL),
                    3-5 økter i uka, progressiv overload, og få gjerne variasjon i øvelsesvalget.""";
            default -> """
                    Tilpasning til avansert: brukeren vet hva de vil ha. Følg ønsket tydelig, bruk avanserte
                    metoder (periodisering, intensitetsteknikker, RPE) når det passer, og hold begrunnelsene
                    korte og tekniske.""";
        };
    }

    /** Sjekker enum-verdier og grenser; trimmer tekst. Kaster IllegalArgumentException (-> 400). */
    static TrainingProfileData validate(TrainingProfileData p) {
        if (p == null) throw new IllegalArgumentException("Mangler profil.");
        String level = oneOf(p.experienceLevel(), LEVELS, "erfaringsnivå");
        String goal = oneOf(p.goal(), GOALS, "mål");
        String equipment = oneOf(p.equipment(), EQUIPMENT, "utstyr");
        String technique = oneOf(p.technique(), TECHNIQUE, "teknikk");
        String style = oneOf(p.explanationStyle(), STYLES, "forklaringsstil");
        return new TrainingProfileData(level,
                range(p.trainingMonths(), 0, 720, "hvor lenge du har trent"),
                range(p.sessionsPerWeek(), 0, 14, "økter per uke"),
                goal, equipment,
                range(p.daysPerWeek(), 1, 7, "dager per uke"),
                range(p.sessionMinutes(), 10, 180, "minutter per økt"),
                technique, style,
                text(p.injuries(), MAX_INJURIES, "skader"),
                text(p.background(), MAX_SHORT_TEXT, "bakgrunn"),
                text(p.strengths(), MAX_SHORT_TEXT, "styrker"),
                text(p.weaknesses(), MAX_SHORT_TEXT, "svakheter"));
    }

    private static String oneOf(String value, Set<String> allowed, String field) {
        String v = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!allowed.contains(v)) throw new IllegalArgumentException("Ugyldig verdi for " + field + ".");
        return v;
    }

    private static int range(int v, int min, int max, String field) {
        if (v < min || v > max) {
            throw new IllegalArgumentException("«" + field + "» må være mellom " + min + " og " + max + ".");
        }
        return v;
    }

    private static String text(String value, int max, String field) {
        String v = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (v.length() > max) throw new IllegalArgumentException("«" + field + "» kan være maks " + max + " tegn.");
        return v;
    }

    private static String months(int m) {
        if (m <= 0) return "ingenting (helt ny)";
        if (m < 12) return m + " måneder";
        int years = m / 12;
        return years + (years == 1 ? " år" : " år") + (m % 12 == 0 ? "" : " og " + (m % 12) + " mnd");
    }

    private static String levelText(String l) {
        return switch (l) {
            case "BEGINNER" -> "nybegynner (helt ny til trening)";
            case "NOVICE" -> "litt erfaren (under ca. 1 år)";
            case "INTERMEDIATE" -> "middels erfaren";
            default -> "avansert";
        };
    }

    private static String goalText(String g) {
        return switch (g) {
            case "STRENGTH" -> "bli sterkere";
            case "MUSCLE" -> "bygge muskler";
            case "FAT_LOSS" -> "gå ned i vekt";
            case "ENDURANCE" -> "kondisjon/utholdenhet";
            default -> "generell helse";
        };
    }

    private static String equipmentText(String e) {
        return switch (e) {
            case "GYM" -> "treningssenter (full tilgang)";
            case "HOME_WEIGHTS" -> "hjemme med manualer/kettlebells";
            default -> "kun kroppsvekt";
        };
    }

    private static String techniqueText(String t) {
        return switch (t) {
            case "LOW" -> "kan lite, trenger veiledning på teknikk";
            case "MEDIUM" -> "kan det grunnleggende";
            default -> "trygg på teknikken";
        };
    }
}
