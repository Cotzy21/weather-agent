package no.weatheragent.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enkel in-memory caching for å skåne de eksterne API-ene. Geografiske data fra
 * Overpass (topper i et område, turruter rundt et punkt) endrer seg nesten aldri,
 * så vi cacher dem og slipper å spørre Overpass på nytt for samme område. Det var
 * gjentatte Overpass-kall som ga 429 Too Many Requests.
 *
 * Cachen lever i minnet og tømmes ved omstart - helt greit for denne bruken.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("peaks", "trails");
    }
}
