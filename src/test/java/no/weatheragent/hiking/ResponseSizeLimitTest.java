package no.weatheragent.hiking;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Størrelsesgrensen på Overpass-svar: et svar over taket avvises før det blir et stort JSON-tre i minnet (gratisinstansen
 * har ca. 330 MB heap og krasjer ved OOM), og et svar under taket går uendret gjennom.
 */
class ResponseSizeLimitTest {

    private static ClientHttpResponse response(long declaredLength, byte[] body) {
        return new ClientHttpResponse() {
            @Override
            public HttpStatusCode getStatusCode() {
                return HttpStatus.OK;
            }

            @Override
            public String getStatusText() {
                return "OK";
            }

            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders h = new HttpHeaders();
                if (declaredLength >= 0) {
                    h.setContentLength(declaredLength);
                }
                return h;
            }

            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public void close() {
            }
        };
    }

    @Test
    void aResponseUnderTheLimitPassesThroughUnchanged() throws IOException {
        var limit = new ResponseSizeLimit(100);
        ClientHttpResponse limited = limit.intercept(null, new byte[0], (req, body) -> response(50, new byte[50]));

        assertEquals(50, limited.getBody().readAllBytes().length);
    }

    @Test
    void aDeclaredContentLengthOverTheLimitIsRefusedBeforeReadingAnything() {
        var limit = new ResponseSizeLimit(100);

        assertThrows(ResponseSizeLimit.TooLargeException.class,
                () -> limit.intercept(null, new byte[0], (req, body) -> response(101, new byte[0])));
    }

    @Test
    void aResponseWithoutContentLengthIsCountedWhileItIsRead() throws IOException {
        var limit = new ResponseSizeLimit(100);
        ClientHttpResponse limited = limit.intercept(null, new byte[0], (req, body) -> response(-1, new byte[500]));

        assertThrows(ResponseSizeLimit.TooLargeException.class, () -> limited.getBody().readAllBytes());
    }

    @Test
    void exactlyAtTheLimitIsAllowed() throws IOException {
        var limit = new ResponseSizeLimit(100);
        ClientHttpResponse limited = limit.intercept(null, new byte[0], (req, body) -> response(-1, new byte[100]));

        assertEquals(100, limited.getBody().readAllBytes().length);
    }

    @Test
    void isTooLargeFindsTheCauseEvenWhenRestClientWrapsIt() {
        var wrapped = new RestClientException("Error while extracting response", new ResponseSizeLimit.TooLargeException(1));

        assertTrue(ResponseSizeLimit.isTooLarge(wrapped));
        assertFalse(ResponseSizeLimit.isTooLarge(new RestClientException("annet")));
        assertFalse(ResponseSizeLimit.isTooLarge(null));
    }

    @Test
    void everyMirrorAnsweringWithAnOversizedBodyEndsAsUnavailableNotACrash() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // gyldig JSON, så det er størrelsesgrensen (ikke en parsefeil) som stopper svaret
        String huge = "{\"elements\":[" + "{\"type\":\"node\",\"id\":1},".repeat((int) OverpassClient.MAX_RESPONSE_BYTES / 20) + "{}]}";
        for (String endpoint : OverpassClient.ENDPOINTS) {
            server.expect(requestTo(endpoint)).andRespond(withSuccess(huge, MediaType.APPLICATION_JSON));
        }
        OverpassClient client = new OverpassClient(OverpassClient.limitResponses(builder).build(), OverpassClient.ENDPOINTS, 6_000, 35_000);

        assertThrows(OverpassUnavailableException.class, () -> client.peaksInArea("Rogaland", "NO"));
        server.verify(); // alle tre speilene ble prøvd
    }

    @Test
    void aMirrorThatAnswersWithAnHtmlErrorPageIsSkippedAndTheNextMirrorIsUsed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(OverpassClient.ENDPOINTS.get(0)))
                .andRespond(withSuccess("<html>The server is probably too busy</html>", MediaType.TEXT_HTML));
        server.expect(requestTo(OverpassClient.ENDPOINTS.get(1)))
                .andRespond(withSuccess("{\"elements\":[{\"type\":\"node\",\"id\":1,\"lat\":59.1,\"lon\":6.2,"
                        + "\"tags\":{\"name\":\"Prekestolen\",\"natural\":\"peak\",\"ele\":\"604\"}}]}", MediaType.APPLICATION_JSON));
        OverpassClient client = new OverpassClient(OverpassClient.limitResponses(builder).build(), OverpassClient.ENDPOINTS, 6_000, 35_000);

        assertEquals("Prekestolen", client.peaksInArea("Rogaland", "NO").getFirst().location().name());
        server.verify();
    }
}
