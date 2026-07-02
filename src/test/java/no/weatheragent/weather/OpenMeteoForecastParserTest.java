package no.weatheragent.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.geo.Location;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenMeteoForecastParserTest {

    private static final Location SLOGEN = new Location("Slogen", 62.18, 6.86);

    @Test
    void parsesHourlyArraysIntoWeatherPoints() throws Exception {
        JsonNode root = new ObjectMapper().readTree("""
                {
                  "latitude": 62.18, "longitude": 6.86, "elevation": 1520.0,
                  "hourly": {
                    "time": ["2026-07-11T10:00", "2026-07-11T11:00"],
                    "temperature_2m": [11.4, 12.1],
                    "precipitation": [0.0, 0.3],
                    "wind_speed_10m": [4.2, 5.0]
                  }
                }
                """);

        Forecast forecast = OpenMeteoForecastParser.parse(SLOGEN, root);

        assertEquals(1520.0, forecast.elevationMeters());
        assertEquals(2, forecast.points().size());

        WeatherPoint first = forecast.points().getFirst();
        assertEquals(Instant.parse("2026-07-11T10:00:00Z"), first.time());
        assertEquals(11.4, first.temperatureC());
        assertEquals(0.0, first.precipitationMm());
        assertEquals(4.2, first.windSpeedMs());

        WeatherPoint second = forecast.points().get(1);
        assertEquals(0.3, second.precipitationMm());
        assertEquals(5.0, second.windSpeedMs());
    }

    @Test
    void emptyHourlyGivesEmptyForecast() throws Exception {
        JsonNode root = new ObjectMapper().readTree("{\"elevation\": 0.0}");

        Forecast forecast = OpenMeteoForecastParser.parse(SLOGEN, root);

        assertEquals(0, forecast.points().size());
    }
}
