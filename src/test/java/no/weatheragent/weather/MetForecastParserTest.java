package no.weatheragent.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.geo.Location;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tester tolkningen av MET sitt JSON mot vart domeneobjekt - uten nettverk.
 */
class MetForecastParserTest {

    private static final String SAMPLE_JSON = """
            {
              "geometry": { "coordinates": [6.0, 62.0, 1564.0] },
              "properties": {
                "timeseries": [
                  {
                    "time": "2026-06-26T10:00:00Z",
                    "data": {
                      "instant": { "details": { "air_temperature": 18.4, "wind_speed": 2.1 } },
                      "next_1_hours": { "details": { "precipitation_amount": 0.0 } }
                    }
                  },
                  {
                    "time": "2026-06-26T11:00:00Z",
                    "data": {
                      "instant": { "details": { "air_temperature": 19.7, "wind_speed": 3.4 } },
                      "next_1_hours": { "details": { "precipitation_amount": 1.2 } }
                    }
                  },
                  {
                    "time": "2026-06-26T12:00:00Z",
                    "data": {
                      "instant": { "details": { "air_temperature": 20.1, "wind_speed": 3.0 } }
                    }
                  }
                ]
              }
            }
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesAllTimeseriesPoints() throws Exception {
        Location location = new Location("Testby", 62.0, 6.0);

        Forecast forecast = MetForecastParser.parse(location, mapper.readTree(SAMPLE_JSON));

        assertEquals(3, forecast.points().size());
        assertEquals(location, forecast.location());
    }

    @Test
    void parsesElevationFromGeometry() throws Exception {
        Forecast forecast = MetForecastParser.parse(
                new Location("Testby", 62.0, 6.0), mapper.readTree(SAMPLE_JSON));

        assertEquals(1564.0, forecast.elevationMeters());
    }

    @Test
    void defaultsElevationToZeroWhenMissing() throws Exception {
        Forecast forecast = MetForecastParser.parse(
                new Location("Testby", 62.0, 6.0),
                mapper.readTree("{ \"properties\": { \"timeseries\": [] } }"));

        assertEquals(0.0, forecast.elevationMeters());
    }

    @Test
    void mapsFieldsCorrectly() throws Exception {
        Forecast forecast = MetForecastParser.parse(
                new Location("Testby", 62.0, 6.0), mapper.readTree(SAMPLE_JSON));

        WeatherPoint first = forecast.points().getFirst();
        assertEquals(Instant.parse("2026-06-26T10:00:00Z"), first.time());
        assertEquals(18.4, first.temperatureC());
        assertEquals(0.0, first.precipitationMm());
        assertEquals(2.1, first.windSpeedMs());
    }

    @Test
    void fallsBackToSixHourPrecipitationFurtherOut() throws Exception {
        // Lenger fram gir MET 6-timers steg uten next_1_hours - da skal vi bruke next_6_hours.
        String sixHourJson = """
                {
                  "properties": {
                    "timeseries": [
                      {
                        "time": "2026-06-29T12:00:00Z",
                        "data": {
                          "instant": { "details": { "air_temperature": 12.0, "wind_speed": 5.0 } },
                          "next_6_hours": { "details": { "precipitation_amount": 4.9 } }
                        }
                      }
                    ]
                  }
                }
                """;

        Forecast forecast = MetForecastParser.parse(
                new Location("Nonstinden", 62.0, 6.0), mapper.readTree(sixHourJson));

        assertEquals(4.9, forecast.points().getFirst().precipitationMm());
    }

    @Test
    void defaultsPrecipitationToZeroWhenMissing() throws Exception {
        Forecast forecast = MetForecastParser.parse(
                new Location("Testby", 62.0, 6.0), mapper.readTree(SAMPLE_JSON));

        // Siste punkt mangler "next_1_hours" - skal bli 0.0, ikke krasje.
        WeatherPoint last = forecast.points().getLast();
        assertEquals(0.0, last.precipitationMm());
        assertEquals(20.1, last.temperatureC());
    }
}
