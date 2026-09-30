package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tidsstyringen mot Overpass: første gyldige svar vinner, feilende speil byttes med en gang, trege speil får selskap av
 * neste speil parallelt, og hele oppslaget har et tak. Testes med falske kall som sover eller feiler på kommando.
 */
class OverpassRaceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> MIRRORS = List.of("main", "kumi", "coffee");

    private static JsonNode answer(String from) {
        return JSON.createObjectNode().put("from", from);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Falsk Overpass: hvert speil har en oppførsel, og vi husker hvem som ble kalt og når. */
    private static class Fake implements Function<String, JsonNode> {
        final List<String> called = new CopyOnWriteArrayList<>();
        final java.util.Map<String, Function<String, JsonNode>> behaviour = new java.util.HashMap<>();

        Fake on(String mirror, Function<String, JsonNode> b) {
            behaviour.put(mirror, b);
            return this;
        }

        @Override
        public JsonNode apply(String mirror) {
            called.add(mirror);
            return behaviour.getOrDefault(mirror, m -> answer(m)).apply(mirror);
        }
    }

    @Test
    void aFastFirstMirrorAnswersAndTheOthersAreNeverBothered() {
        Fake fake = new Fake();

        JsonNode result = OverpassClient.raceMirrors(MIRRORS, fake, 500, 5_000);

        assertEquals("main", result.get("from").asText());
        assertEquals(List.of("main"), fake.called);
    }

    @Test
    void aBusyMirrorIsReplacedAtOnceWithoutWaitingForTheHedgeDelay() {
        Fake fake = new Fake().on("main", m -> {
            throw new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT);
        });

        long t0 = System.nanoTime();
        JsonNode result = OverpassClient.raceMirrors(MIRRORS, fake, 10_000, 30_000);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertEquals("kumi", result.get("from").asText());
        assertEquals(List.of("main", "kumi"), fake.called);
        assertTrue(ms < 2_000, "byttet til neste speil straks, brukte " + ms + " ms");
    }

    @Test
    void aSlowMirrorGetsCompanyInParallelAndTheFastestAnswerWins() {
        Fake fake = new Fake().on("main", m -> {
            sleep(3_000); // henger
            return answer(m);
        });

        long t0 = System.nanoTime();
        JsonNode result = OverpassClient.raceMirrors(MIRRORS, fake, 100, 30_000);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertEquals("kumi", result.get("from").asText());
        assertTrue(ms < 1_500, "ventet ikke på det hengende speilet, brukte " + ms + " ms");
        assertTrue(fake.called.contains("main") && fake.called.contains("kumi"));
    }

    @Test
    void theFirstMirrorStillWinsIfItAnswersBeforeTheHelperFails() {
        Fake fake = new Fake().on("main", m -> {
            sleep(300);
            return answer(m);
        }).on("kumi", m -> {
            throw new ResourceAccessException("connection reset");
        }).on("coffee", m -> {
            sleep(5_000);
            return answer(m);
        });

        JsonNode result = OverpassClient.raceMirrors(MIRRORS, fake, 100, 30_000);

        assertEquals("main", result.get("from").asText());
    }

    @Test
    void whenEveryMirrorIsBusyItGivesAFriendlyUnavailableErrorNotTheRawFailure() {
        Fake fake = new Fake();
        MIRRORS.forEach(m -> fake.on(m, x -> {
            throw new ResourceAccessException("timeout");
        }));

        OverpassUnavailableException e = assertThrows(OverpassUnavailableException.class,
                () -> OverpassClient.raceMirrors(MIRRORS, fake, 50, 10_000));

        assertEquals(OverpassUnavailableException.USER_MESSAGE, e.getMessage());
        assertInstanceOf(ResourceAccessException.class, e.getCause());
        assertEquals(3, fake.called.size());
    }

    @Test
    void anOverloadedMirrorAnswering429CountsAsBusyLikeA5xx() {
        Fake fake = new Fake().on("main", m -> {
            throw new org.springframework.web.client.HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
        });

        assertEquals("kumi", OverpassClient.raceMirrors(MIRRORS, fake, 500, 5_000).get("from").asText());
    }

    @Test
    void aMistakeInOurOwnQueryIsThrownAtOnceBecauseAnotherMirrorCannotHelp() {
        Fake fake = new Fake().on("main", m -> {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST);
        });

        assertThrows(HttpClientErrorException.class, () -> OverpassClient.raceMirrors(MIRRORS, fake, 500, 5_000));
        assertEquals(List.of("main"), fake.called);
    }

    @Test
    void theTotalBudgetEndsTheWaitEvenWhenEveryMirrorHangs() {
        Fake fake = new Fake();
        MIRRORS.forEach(m -> fake.on(m, x -> {
            sleep(4_000);
            return answer(x);
        }));

        long t0 = System.nanoTime();
        assertThrows(OverpassUnavailableException.class, () -> OverpassClient.raceMirrors(MIRRORS, fake, 50, 400));
        long ms = (System.nanoTime() - t0) / 1_000_000;

        assertTrue(ms < 1_500, "ga opp innen tidstaket, brukte " + ms + " ms");
    }

    @Test
    void anEmptyResponseCountsAsAFailedMirrorAndTheNextOneIsTried() {
        Fake fake = new Fake().on("main", m -> null);

        assertEquals("kumi", OverpassClient.raceMirrors(MIRRORS, fake, 500, 5_000).get("from").asText());
    }

    @Test
    void mirrorsThatLoseTheRaceAreInterruptedSoTheyStopDownloading() throws Exception {
        java.util.concurrent.CountDownLatch slowStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch slowInterrupted = new java.util.concurrent.CountDownLatch(1);
        Fake fake = new Fake()
                .on("main", m -> {
                    slowStarted.countDown();
                    try {
                        Thread.sleep(30_000);
                    } catch (InterruptedException e) {
                        slowInterrupted.countDown();
                    }
                    return answer(m);
                })
                .on("kumi", m -> {
                    await(slowStarted);
                    return answer(m);
                });

        assertEquals("kumi", OverpassClient.raceMirrors(MIRRORS, fake, 50, 5_000).get("from").asText());

        assertTrue(slowInterrupted.await(2, java.util.concurrent.TimeUnit.SECONDS), "det trege speilet skal avbrytes når kumi har vunnet");
    }

    @Test
    void mirrorsStillHangingWhenWeGiveUpAreInterruptedToo() throws Exception {
        java.util.concurrent.CountDownLatch interrupted = new java.util.concurrent.CountDownLatch(3);
        Fake fake = new Fake();
        MIRRORS.forEach(m -> fake.on(m, x -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return answer(x);
        }));

        assertThrows(OverpassUnavailableException.class, () -> OverpassClient.raceMirrors(MIRRORS, fake, 20, 300));

        assertTrue(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS), "alle tre hengende kall skal avbrytes ved oppgivelse");
    }

    @Test
    void whenTheAppAlreadyHasTheMaximumOfCallsInFlightNewOnesFailAtOnceWithoutCallingOut() {
        int taken = 0;
        while (OverpassClient.IN_FLIGHT.tryAcquire()) {
            taken++;
        }
        try {
            Fake fake = new Fake();
            long t0 = System.nanoTime();
            assertThrows(OverpassUnavailableException.class, () -> OverpassClient.raceMirrors(MIRRORS, fake, 500, 5_000));
            long ms = (System.nanoTime() - t0) / 1_000_000;

            assertEquals(List.of(), fake.called, "ingen forespørsel skal gå ut når taket er nådd");
            assertTrue(ms < 1_000, "skal gi opp umiddelbart, brukte " + ms + " ms");
        } finally {
            OverpassClient.IN_FLIGHT.release(taken);
        }
    }

    @Test
    void everyCallGivesItsPermitBackWhetherItSucceedsFailsOrIsInterrupted() throws Exception {
        Fake ok = new Fake();
        OverpassClient.raceMirrors(MIRRORS, ok, 500, 5_000);
        Fake bad = new Fake();
        MIRRORS.forEach(m -> bad.on(m, x -> {
            throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
        }));
        assertThrows(OverpassUnavailableException.class, () -> OverpassClient.raceMirrors(MIRRORS, bad, 500, 5_000));
        Fake hang = new Fake().on("main", m -> {
            sleep(30_000);
            return answer(m);
        });
        OverpassClient.raceMirrors(MIRRORS, hang, 20, 5_000); // kumi vinner, main avbrytes

        long until = System.currentTimeMillis() + 2_000;
        while (OverpassClient.IN_FLIGHT.availablePermits() < OverpassClient.MAX_IN_FLIGHT && System.currentTimeMillis() < until) {
            Thread.sleep(20);
        }
        assertEquals(OverpassClient.MAX_IN_FLIGHT, OverpassClient.IN_FLIGHT.availablePermits(), "tillatelser lekker");
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void theDefaultBudgetLeavesRoomForTheHostsRequestLimitOfAbout100Seconds() {
        assertTrue(OverpassClient.TOTAL_BUDGET_MS <= 40_000, "oppslaget må gi opp lenge før Render kutter (ca. 100 s)");
        assertTrue(OverpassClient.HEDGE_DELAY_MS < OverpassClient.TOTAL_BUDGET_MS);
    }
}
