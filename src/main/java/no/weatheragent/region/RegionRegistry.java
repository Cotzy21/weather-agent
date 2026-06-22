package no.weatheragent.region;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.geo.Location;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Laster den kuraterte region->steder-lista fra resources/regions.json
 * én gang ved oppstart, og lar oss slå opp regioner på navn.
 *
 * Oppslag er ufølsomt for store/små bokstaver, så "Sunnmøre" og "sunnmøre"
 * gir samme treff.
 */
@Component
public class RegionRegistry {

    private static final String RESOURCE = "/regions.json";

    /** Nøkkel = regionnavn i små bokstaver. Verdi = den ferdige regionen. */
    private final Map<String, Region> regionsByLowerName;

    public RegionRegistry(ObjectMapper mapper) {
        this.regionsByLowerName = load(mapper);
    }

    private Map<String, Region> load(ObjectMapper mapper) {
        try (InputStream in = getClass().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Fant ikke " + RESOURCE + " på classpath");
            }

            // JSON-formen er { "Sunnmøre": [ {sted}, ... ], ... }
            Map<String, List<Location>> raw =
                    mapper.readValue(in, new TypeReference<>() {
                    });

            Map<String, Region> result = new LinkedHashMap<>();
            raw.forEach((name, places) ->
                    result.put(name.toLowerCase(Locale.ROOT), new Region(name, places)));
            return result;

        } catch (IOException e) {
            throw new UncheckedIOException("Klarte ikke å lese " + RESOURCE, e);
        }
    }

    /** Finn en region på navn (ufølsomt for store/små bokstaver). */
    public Optional<Region> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(regionsByLowerName.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    /** Alle regionnavn vi kjenner til (med original skrivemåte). */
    public Set<String> regionNames() {
        return regionsByLowerName.values().stream()
                .map(Region::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
