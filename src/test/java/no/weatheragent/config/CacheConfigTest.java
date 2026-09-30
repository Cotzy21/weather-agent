package no.weatheragent.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheConfigTest {

    @Test
    void cachesStayBoundedSoAttackersCannotFillTheMemory() {
        CacheManager manager = new CacheConfig().cacheManager();
        Cache cache = manager.getCache("trailGeometry");
        assertNotNull(cache);
        var nativeCache = (com.github.benmanes.caffeine.cache.Cache<Object, Object>) cache.getNativeCache();

        for (int i = 0; i < 5_000; i++) {
            cache.put("lat" + i, "x");
        }
        nativeCache.cleanUp();

        assertTrue(nativeCache.estimatedSize() <= CacheConfig.MAX_ENTRIES,
                "cachen vokste til " + nativeCache.estimatedSize());
    }

    @Test
    void foodsCacheStillExists() {
        assertNotNull(new CacheConfig().cacheManager().getCache("foods"));
    }
}
