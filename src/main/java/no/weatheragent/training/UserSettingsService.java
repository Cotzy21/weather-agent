package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Små JSON-innstillinger per bruker som følger kontoen på tvers av enheter. Kun kjente nøkler. */
@Service
public class UserSettingsService {

    public static final String DASHBOARD = "dashboard";
    public static final String PROGRAM = "program";
    /** Ukeserien: {@code {"goal": 3, "pauses": ["2026-09-28"]}} (mål i treningsdager per uke, pausede uker som datoer). */
    public static final String STREAK = "streak";
    static final Set<String> KEYS = Set.of(DASHBOARD, PROGRAM, STREAK);
    /** Nøkler frontenden kan lese og skrive direkte via /api/innstillinger/{key}. Programmet har egne endepunkter. */
    static final Set<String> CLIENT_KEYS = Set.of(DASHBOARD, STREAK);
    static final int MAX_JSON_CHARS = 8_000;
    static final int MIN_GOAL = 1;
    static final int MAX_GOAL = 7;
    static final int MAX_PAUSES = 52;

    private final UserSettingsRepository repository;

    public UserSettingsService(UserSettingsRepository repository) {
        this.repository = repository;
    }

    public static boolean isKnown(String key) {
        return KEYS.contains(key);
    }

    public static boolean isClientKey(String key) {
        return CLIENT_KEYS.contains(key);
    }

    @Transactional(readOnly = true)
    public Optional<JsonNode> get(UUID userId, String key) {
        return repository.findById(new UserSettings.Key(userId, key)).map(UserSettings::getValue);
    }

    @Transactional
    public JsonNode put(UUID userId, String key, JsonNode value) {
        if (!isKnown(key)) throw new IllegalArgumentException("Ukjent innstilling.");
        if (value == null || value.isNull() || value.toString().length() > MAX_JSON_CHARS) {
            throw new IllegalArgumentException("Ugyldig innstilling.");
        }
        JsonNode stored = STREAK.equals(key) ? normalizeStreak(value) : value;
        repository.save(new UserSettings(userId, key, stored));
        return stored;
    }

    /**
     * Ukeserie-innstillingen lagres bare med kjente felt og gyldige verdier (mål 1-7, høyst 52 pausede uker som datoer), så
     * en klient ikke kan legge vilkårlig JSON i kontoen. Pausene sorteres og dedupliseres.
     */
    static JsonNode normalizeStreak(JsonNode value) {
        if (!value.isObject()) throw new IllegalArgumentException("Ugyldig innstilling.");
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        JsonNode goal = value.get("goal");
        if (goal != null && !goal.isNull()) {
            if (!goal.isInt() || goal.asInt() < MIN_GOAL || goal.asInt() > MAX_GOAL) {
                throw new IllegalArgumentException("Målet må være et helt tall fra " + MIN_GOAL + " til " + MAX_GOAL + ".");
            }
            out.put("goal", goal.asInt());
        }
        JsonNode pauses = value.get("pauses");
        if (pauses != null && !pauses.isNull()) {
            if (!pauses.isArray() || pauses.size() > MAX_PAUSES) throw new IllegalArgumentException("Ugyldige pauser.");
            TreeSet<String> dates = new TreeSet<>();
            for (JsonNode p : pauses) {
                try {
                    if (!p.isTextual()) throw new IllegalArgumentException("Ugyldige pauser.");
                    dates.add(LocalDate.parse(p.asText()).toString());
                } catch (DateTimeException e) {
                    throw new IllegalArgumentException("Ugyldige pauser.");
                }
            }
            ArrayNode array = out.putArray("pauses");
            dates.forEach(array::add);
        }
        return out;
    }

    @Transactional
    public void delete(UUID userId, String key) {
        repository.deleteById(new UserSettings.Key(userId, key));
    }
}
