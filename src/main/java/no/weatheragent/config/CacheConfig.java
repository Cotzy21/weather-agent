package no.weatheragent.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-memory caching for å skåne de eksterne API-ene. Geografiske data fra Overpass (topper i et
 * område, turruter rundt et punkt) endrer seg nesten aldri, så vi cacher dem og slipper å spørre
 * Overpass på nytt for samme område. Det var gjentatte Overpass-kall som ga 429 Too Many Requests.
 *
 * Cachene er BEGRENSET (størrelse + levetid): nøklene kommer fra åpne endepunkter (områdenavn,
 * koordinater), så en ubegrenset cache lot hvem som helst fylle minnet til serveren gikk tom.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    static final int MAX_ENTRIES = 500;
    static final Duration TTL = Duration.ofHours(6);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(MAX_ENTRIES).expireAfterWrite(TTL));
        // "foods" = hele Matvaretabellen (~2100 varer): én oppføring, hentes sjelden på nytt.
        manager.registerCustomCache("foods",
                Caffeine.newBuilder().maximumSize(1).expireAfterWrite(Duration.ofHours(24)).build());
        return manager;
    }
}
