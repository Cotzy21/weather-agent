package no.weatheragent.nutrition;

import no.weatheragent.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MatvaretabellenClientTest {
    private static final String URL = "https://www.matvaretabellen.no/api/nb/foods.json";
    private static final String FOOD = "{\"foods\":[{\"foodId\":\"1\",\"foodName\":\"Havre\",\"calories\":{\"quantity\":370}}]}";

    @Test
    void streamsResponseAndPreservesServerErrorRetry() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withSuccess(FOOD, MediaType.APPLICATION_JSON));
        var foods = new MatvaretabellenClient(builder).allFoods();
        assertEquals("Havre", foods.getFirst().name());
        assertEquals(370, foods.getFirst().kcalPer100g());
        server.verify();
    }

    @Test
    void doesNotRetryClientErrors() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(HttpClientErrorException.class, () -> new MatvaretabellenClient(builder).allFoods());
        server.verify();
    }

    @Test
    void concurrentColdSearchesShareOneDownloadThroughRealCacheProxy() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.expect(requestTo(URL)).andRespond(request -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test response timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return withSuccess(FOOD, MediaType.APPLICATION_JSON).createResponse(request);
        });
        try (var context = new AnnotationConfigApplicationContext(); var pool = Executors.newFixedThreadPool(4)) {
            context.register(CacheConfig.class);
            context.registerBean(MatvaretabellenClient.class, () -> new MatvaretabellenClient(builder));
            context.refresh();
            var client = context.getBean(MatvaretabellenClient.class);
            var first = pool.submit(client::allFoods);
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = pool.submit(client::allFoods);
            var third = pool.submit(client::allFoods);
            var fourth = pool.submit(client::allFoods);
            release.countDown();
            var result = first.get(5, TimeUnit.SECONDS);
            assertSame(result, second.get(5, TimeUnit.SECONDS));
            assertSame(result, third.get(5, TimeUnit.SECONDS));
            assertSame(result, fourth.get(5, TimeUnit.SECONDS));
            assertSame(result, client.allFoods());
            server.verify();
        } finally {
            release.countDown();
        }
    }
}
