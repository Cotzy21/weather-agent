package no.weatheragent.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(1000, 5000);

    private MockHttpServletResponse run(MockHttpServletRequest req, boolean[] reached) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, (r, s) -> {
            reached[0] = true;
            r.getInputStream().readAllBytes(); // som Jackson: leser hele kroppen
        });
        return res;
    }

    @Test
    void smallBodyPasses() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/treningsokter");
        req.setContent(new byte[500]);
        boolean[] reached = {false};
        assertEquals(200, run(req, reached).getStatus());
        assertTrue(reached[0]);
    }

    @Test
    void declaredOversizeIsRejectedBeforeAnythingIsRead() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/treningsokter");
        req.setContent(new byte[2000]);
        boolean[] reached = {false};
        assertEquals(413, run(req, reached).getStatus());
        assertTrue(!reached[0]);
    }

    @Test
    void undeclaredOversizeIsStoppedWhileReading() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/treningsokter") {
            @Override
            public long getContentLengthLong() {
                return -1; // chunked: ingen lengde oppgitt
            }
        };
        req.setContent(new byte[2000]);
        boolean[] reached = {false};
        assertEquals(413, run(req, reached).getStatus());
        assertTrue(reached[0]);
    }

    @Test
    void garminImportHasItsOwnLargerLimit() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/trening/import/garmin");
        req.setContent(new byte[3000]);
        boolean[] reached = {false};
        assertEquals(200, run(req, reached).getStatus());
    }

    @Test
    void fitImportAlsoGetsTheLargerImportLimit() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/trening/import/fit");
        req.setContent(new byte[3000]);
        boolean[] reached = {false};
        assertEquals(200, run(req, reached).getStatus());
    }
}
