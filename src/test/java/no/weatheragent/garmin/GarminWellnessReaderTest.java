package no.weatheragent.garmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GarminWellnessReaderTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sleepKeepsWakeDateAndClockAcrossMidnightAndDoesNotInventScore() throws Exception {
        var row = json.readTree("""
                {"sleepTimeSeconds":25200,"deepSleepSeconds":3600,
                 "sleepStartTimestampLocal":"2026-10-24T23:15:00",
                 "sleepEndTimestampLocal":"2026-10-25T06:30:00"}
                """);
        var wake = GarminWellnessReader.localTimestamp(row.path("sleepEndTimestampLocal"));
        var metrics = GarminWellnessReader.sleep(row, wake);
        assertEquals(LocalDateTime.of(2026, 10, 25, 6, 30), wake);
        assertEquals(420.0, metrics.get("sleepMin"));
        assertEquals(1395.0, metrics.get("bedtime"));
        assertEquals(390.0, metrics.get("wakeTime"));
        assertFalse(metrics.containsKey("sleepScore"));
    }

    @Test
    void dailyActivityDoesNotMixRestingAndActiveCaloriesAndKeepsRecordedZero() throws Exception {
        var row = json.readTree("""
                {"movement":{"steps":{"value":0,"goal":8000}},
                 "calories":{"burnedResting":1700,"burnedActive":220,"burnedTotal":1920},
                 "heartRate":{"restingValue":56},"stress":{"avgLevel":-1}}
                """);
        var metrics = GarminWellnessReader.daily(row);
        assertEquals(0.0, metrics.get("steps"));
        assertEquals(8000.0, metrics.get("stepGoal"));
        assertEquals(220.0, metrics.get("activeKcal"));
        assertEquals(1700.0, metrics.get("restingKcal"));
        assertFalse(metrics.containsKey("stress"));
    }
}
