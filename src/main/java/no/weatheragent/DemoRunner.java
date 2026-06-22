package no.weatheragent;

import no.weatheragent.geo.Location;
import no.weatheragent.geo.OpenMeteoGeocodingClient;
import no.weatheragent.weather.Forecast;
import no.weatheragent.weather.MetWeatherClient;
import no.weatheragent.weather.WeatherPoint;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Midlertidig demo: kjorer ved oppstart slik at vi ser at vaerklienten
 * faktisk virker mot det ekte MET-API-et. Fjernes nar vi far et REST-lag.
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE dd.MM HH:mm");

    private final MetWeatherClient weatherClient;
    private final OpenMeteoGeocodingClient geocodingClient;

    public DemoRunner(MetWeatherClient weatherClient, OpenMeteoGeocodingClient geocodingClient) {
        this.weatherClient = weatherClient;
        this.geocodingClient = geocodingClient;
    }

    @Override
    public void run(String... args) {
        // Hele kjeden: stedsnavn -> koordinat (geocoding) -> vaer (MET).
        String searchTerm = "Ålesund";
        Optional<Location> match = geocodingClient.findFirst(searchTerm);

        if (match.isEmpty()) {
            System.out.println("Fant ingen treff for '" + searchTerm + "'");
            return;
        }

        Location place = match.get();
        System.out.printf("%n=== Værvarsel for %s (%.4f, %.4f) ===%n",
                place.name(), place.latitude(), place.longitude());

        Forecast forecast = weatherClient.fetch(place);
        forecast.points().stream()
                .limit(8)
                .forEach(MetDemoPrinter::print);
        System.out.println("(" + forecast.points().size() + " tidspunkter totalt)\n");
    }

    private static final class MetDemoPrinter {
        static void print(WeatherPoint p) {
            String when = p.time().atZone(OSLO).format(FMT);
            System.out.printf("%s  %5.1f°C  %4.1f mm regn  %4.1f m/s vind%n",
                    when, p.temperatureC(), p.precipitationMm(), p.windSpeedMs());
        }
    }
}
