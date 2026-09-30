package no.weatheragent.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Ukeserie-innstillingen lagres bare med kjente felt og gyldige verdier. */
class UserSettingsServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String s) throws Exception {
        return JSON.readTree(s);
    }

    @Test
    void storesOnlyKnownFieldsWithSortedAndDeduplicatedPauses() throws Exception {
        JsonNode out = UserSettingsService.normalizeStreak(
                json("{\"goal\":4,\"pauses\":[\"2026-09-28\",\"2026-09-14\",\"2026-09-28\"],\"evil\":{\"x\":1}}"));

        assertEquals(4, out.get("goal").asInt());
        assertEquals("[\"2026-09-14\",\"2026-09-28\"]", out.get("pauses").toString());
        assertFalse(out.has("evil"));
    }

    @Test
    void allowsAnEmptySettingSoTheDefaultsApply() throws Exception {
        assertEquals("{}", UserSettingsService.normalizeStreak(json("{}")).toString());
    }

    @Test
    void rejectsAGoalOutsideOneToSevenOrThatIsNotAnInteger() throws Exception {
        for (String bad : new String[] {"{\"goal\":0}", "{\"goal\":8}", "{\"goal\":\"3\"}", "{\"goal\":2.5}", "{\"goal\":-1}"}) {
            String input = bad;
            assertThrows(IllegalArgumentException.class, () -> UserSettingsService.normalizeStreak(json(input)), bad);
        }
    }

    @Test
    void rejectsPausesThatAreNotDatesOrAreTooMany() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> UserSettingsService.normalizeStreak(json("{\"pauses\":[\"i går\"]}")));
        assertThrows(IllegalArgumentException.class, () -> UserSettingsService.normalizeStreak(json("{\"pauses\":[\"2026-02-30\"]}")));
        assertThrows(IllegalArgumentException.class, () -> UserSettingsService.normalizeStreak(json("{\"pauses\":[20260928]}")));
        assertThrows(IllegalArgumentException.class, () -> UserSettingsService.normalizeStreak(json("{\"pauses\":\"2026-09-28\"}")));

        StringBuilder many = new StringBuilder("{\"pauses\":[");
        for (int i = 0; i < 53; i++) many.append(i == 0 ? "" : ",").append("\"2026-01-").append(String.format("%02d", (i % 28) + 1)).append('"');
        many.append("]}");
        assertThrows(IllegalArgumentException.class, () -> UserSettingsService.normalizeStreak(json(many.toString())));
    }

    @Test
    void rejectsSomethingThatIsNotAnObject() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> UserSettingsService.normalizeStreak(json("[1,2]")));
        assertThrows(IllegalArgumentException.class, () -> UserSettingsService.normalizeStreak(json("\"tekst\"")));
    }

    @Test
    void putSavesTheNormalizedValueNotTheRawOne() throws Exception {
        UserSettingsRepository repository = mock(UserSettingsRepository.class);
        UserSettingsService service = new UserSettingsService(repository);

        JsonNode stored = service.put(UUID.randomUUID(), "streak", json("{\"goal\":3,\"junk\":true}"));

        assertEquals("{\"goal\":3}", stored.toString());
        verify(repository).save(any(UserSettings.class));
    }

    @Test
    void putRejectsInvalidStreakWithoutSaving() throws Exception {
        UserSettingsRepository repository = mock(UserSettingsRepository.class);
        UserSettingsService service = new UserSettingsService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.put(UUID.randomUUID(), "streak", json("{\"goal\":99}")));
        verify(repository, never()).save(any(UserSettings.class));
    }

    @Test
    void onlyDashboardAndStreakAreClientKeys() {
        assertTrue(UserSettingsService.isClientKey("dashboard"));
        assertTrue(UserSettingsService.isClientKey("streak"));
        assertFalse(UserSettingsService.isClientKey("program"));
        assertTrue(UserSettingsService.isKnown("program"));
    }
}
