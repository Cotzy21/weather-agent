package no.weatheragent.hiking;

import com.sun.net.httpserver.HttpServer;
import no.weatheragent.geo.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turruter rundt et sted er valgfrie og har en kortere frist enn topper og ruter som selve rangeringen trenger. Testes mot
 * en lokal server som aldri svarer, så fristene måles i ekte tid.
 */
class OverpassOptionalBudgetTest {

    private HttpServer hanging;
    private OverpassClient client;

    @BeforeEach
    void startHangingMirror() throws Exception {
        hanging = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        hanging.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        hanging.createContext("/", exchange -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        hanging.start();
        String url = "http://127.0.0.1:" + hanging.getAddress().getPort() + "/api/interpreter";
        // vanlig frist 2 s, valgfri frist 0,3 s
        client = new OverpassClient(RestClient.create(), List.of(url), 5_000, 2_000, 100, 300);
    }

    @AfterEach
    void stop() {
        hanging.stop(0);
    }

    private static long millis(Runnable r) {
        long t0 = System.nanoTime();
        try {
            r.run();
        } catch (OverpassUnavailableException expected) {
            // brukt til å måle hvor lenge vi ventet
        }
        return (System.nanoTime() - t0) / 1_000_000;
    }

    @Test
    void theTrailLookupsAroundAPlaceGiveUpAfterTheShortBudget() {
        Location place = new Location("Slogen", 62.18, 6.86);

        long geometry = millis(() -> client.trailGeometriesNear(place, 8000));
        long around = millis(() -> client.trailsNear(List.of(place), 8000));

        assertTrue(geometry < 1_500, "detaljsidens turruter skal gi opp raskt, brukte " + geometry + " ms");
        assertTrue(around < 1_500, "turruter i søket skal gi opp raskt, brukte " + around + " ms");
    }

    @Test
    void peaksAndAreaTrailsThatTheRankingNeedsKeepTheFullBudget() {
        long peaks = millis(() -> client.peaksInArea("Rogaland", "NO"));
        long trailsInArea = millis(() -> client.trailsInArea("Rogaland", "NO"));

        assertTrue(peaks >= 1_800, "topper skal få hele fristen, brukte " + peaks + " ms");
        assertTrue(trailsInArea >= 1_800, "ruter i området skal få hele fristen, brukte " + trailsInArea + " ms");
    }

    @Test
    void theDefaultOptionalBudgetIsShorterThanTheFullOneAndStillLeavesRoomForAHedge() {
        assertTrue(OverpassClient.OPTIONAL_BUDGET_MS <= 10_000);
        assertTrue(OverpassClient.OPTIONAL_BUDGET_MS < OverpassClient.TOTAL_BUDGET_MS);
        assertTrue(OverpassClient.OPTIONAL_HEDGE_DELAY_MS < OverpassClient.OPTIONAL_BUDGET_MS);
    }
}
