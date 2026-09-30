package no.weatheragent.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final Clock clock = new Clock() {
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    };
    private final RateLimitFilter filter = new RateLimitFilter(true, true, clock);
    private final FilterChain chain = (req, res) -> { };

    private MockHttpServletResponse call(String method, String uri, String forwardedFor) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr("10.0.0.1"); // proxyens adresse
        if (forwardedFor != null) req.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }

    @Test
    void weatherSearchIsLimitedPerIpPerMinuteWithRetryAfter() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(200, call("GET", "/api/turvaer", "1.2.3.4").getStatus());
        }
        MockHttpServletResponse blocked = call("GET", "/api/turvaer", "1.2.3.4");
        assertEquals(429, blocked.getStatus());
        assertTrue(Long.parseLong(blocked.getHeader("Retry-After")) > 0);
        assertTrue(blocked.getContentAsString().contains("For mange forespørsler"));
    }

    @Test
    void otherIpsAreNotAffected() throws Exception {
        for (int i = 0; i < 11; i++) call("GET", "/api/turvaer", "1.2.3.4");
        assertEquals(200, call("GET", "/api/turvaer", "5.6.7.8").getStatus());
    }

    @Test
    void windowSlidesSoTheIpCanTryAgainLater() throws Exception {
        for (int i = 0; i < 11; i++) call("GET", "/api/turvaer", "1.2.3.4");
        now.addAndGet(61_000);
        assertEquals(200, call("GET", "/api/turvaer", "1.2.3.4").getStatus());
    }

    @Test
    void globalCapStopsAnAttackerThatRotatesIps() throws Exception {
        int blocked = 0;
        // 700 ulike IP-er, ett kall hver, spredt over tid så per-IP-grensene ikke slår inn
        for (int i = 0; i < 700; i++) {
            now.addAndGet(1_000);
            if (call("GET", "/api/turvaer", "9.9." + (i / 250) + "." + (i % 250)).getStatus() == 429) blocked++;
        }
        assertTrue(blocked > 0, "den globale grensen på 600/time må stoppe noen");
    }

    @Test
    void spoofedGarbageInForwardedForFallsBackToTheRealAddress() throws Exception {
        assertEquals("10.0.0.1", filter.clientIp(reqWith("<script>alert(1)</script>")));
        assertEquals("1.2.3.4", filter.clientIp(reqWith("1.2.3.4, 10.0.0.1")));
    }

    @Test
    void preflightRequestsAreNeverCounted() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertEquals(200, call("OPTIONS", "/api/turvaer", "1.2.3.4").getStatus());
        }
    }

    @Test
    void nonApiPathsAreNotLimited() throws Exception {
        for (int i = 0; i < 500; i++) {
            assertEquals(200, call("GET", "/assets/app.js", "1.2.3.4").getStatus());
        }
    }

    private MockHttpServletRequest reqWith(String xff) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/turvaer");
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", xff);
        return req;
    }

    @Test
    void aiEndpointsShareOneBudgetPerIpAcrossAccounts() throws Exception {
        for (int i = 0; i < 5; i++) call("POST", "/api/trening/forslag", "1.2.3.4");
        for (int i = 0; i < 5; i++) call("POST", "/api/trening/assistent", "1.2.3.4");
        assertEquals(429, call("POST", "/api/trening/plan-forslag", "1.2.3.4").getStatus());
        assertEquals(200, call("GET", "/api/trening/planer", "1.2.3.4").getStatus()); // ikke-AI berøres ikke
    }
}
