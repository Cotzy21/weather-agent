package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Små JSON-innstillinger per bruker som følger kontoen på tvers av enheter. Kun kjente nøkler. */
@Service
public class UserSettingsService {

    public static final String DASHBOARD = "dashboard";
    public static final String PROGRAM = "program";
    static final Set<String> KEYS = Set.of(DASHBOARD, PROGRAM);
    static final int MAX_JSON_CHARS = 8_000;

    private final UserSettingsRepository repository;

    public UserSettingsService(UserSettingsRepository repository) {
        this.repository = repository;
    }

    public static boolean isKnown(String key) {
        return KEYS.contains(key);
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
        repository.save(new UserSettings(userId, key, value));
        return value;
    }

    @Transactional
    public void delete(UUID userId, String key) {
        repository.deleteById(new UserSettings.Key(userId, key));
    }
}
