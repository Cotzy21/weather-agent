package no.weatheragent.hiking;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

/**
 * Tester failover-logikken mellom Overpass-speilene med en mock-server,
 * uten å ringe de ekte instansene.
 */
class OverpassClientTest {

    private static final String PEAKS_JSON = """
            {"elements":[
              {"type":"node","id":1,"lat":62.18,"lon":6.86,
               "tags":{"name":"Slogen","ele":"1564"}}
            ]}
            """;

    @Test
    void overbelastetHovedinstansFallerTilbakePaaNesteSpeil() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 429 fra hovedinstansen skal IKKE gi opp, men gå videre til speilet.
        server.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andRespond(withTooManyRequests());
        server.expect(requestTo("https://overpass.kumi.systems/api/interpreter"))
                .andRespond(withSuccess(PEAKS_JSON, MediaType.APPLICATION_JSON));

        List<Peak> peaks = new OverpassClient(builder).peaksInArea("Sunnmøre", "NO");

        assertEquals(1, peaks.size());
        assertEquals("Slogen", peaks.getFirst().location().name());
        server.verify();
    }

    @Test
    void egenFeilISporringenKastesUtenSpeilbytte() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 400 betyr ugyldig spørring fra oss - speilbytte er meningsløst.
        server.expect(requestTo("https://overpass-api.de/api/interpreter"))
                .andRespond(withBadRequest());

        OverpassClient client = new OverpassClient(builder);
        assertThrows(HttpClientErrorException.class,
                () -> client.peaksInArea("Sunnmøre", "NO"));
        server.verify();
    }
}
