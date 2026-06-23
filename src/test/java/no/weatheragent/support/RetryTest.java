package no.weatheragent.support;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class RetryTest {

    @Test
    void retriesTransientFailureThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = Retry.withRetry(3, 0, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new ResourceAccessException("timeout"); // flyktig
            }
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, calls.get(), "skal ha prøvd tre ganger");
    }

    @Test
    void givesUpAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();
        ResourceAccessException last = new ResourceAccessException("still busy");

        ResourceAccessException thrown = assertThrows(ResourceAccessException.class, () ->
                Retry.withRetry(3, 0, () -> {
                    calls.incrementAndGet();
                    throw last;
                }));

        assertEquals(3, calls.get());
        assertSame(last, thrown, "skal kaste den siste feilen");
    }

    @Test
    void doesNotRetryNonTransientFailure() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalArgumentException.class, () ->
                Retry.withRetry(3, 0, () -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("vår egen feil");
                }));

        assertEquals(1, calls.get(), "ikke-flyktige feil skal ikke prøves på nytt");
    }
}
