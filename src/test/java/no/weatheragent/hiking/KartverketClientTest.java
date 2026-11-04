package no.weatheragent.hiking;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.weatheragent.geo.Location;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KartverketClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Test void foreignRegionsDoNotCallTheNationalService() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new KartverketClient(builder.build());
        assertTrue(client.peaksInArea("Tirol", "AT").isEmpty());
        assertTrue(client.trailsInArea("Tirol", "AT").isEmpty());
        server.verify();
    }
    @Test void countySummitsUseNationalNamesAndActualHeights() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ws.geonorge.no/kommuneinfo/v1/fylker"))
            .andRespond(withSuccess("[{\"fylkesnavn\":\"Rogaland\",\"fylkesnummer\":\"11\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("fnr=11")))
            .andRespond(withSuccess("""
                {"metadata":{"totaltAntallTreff":1},"navn":[{"stedsnummer":1,"representasjonspunkt":{"nord":59,"øst":6},
                  "stedsnavn":[{"skrivemåte":"Testtoppen"}]}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/hoydedata/v1/punkt")))
            .andRespond(withSuccess("{\"punkter\":[{\"x\":6,\"y\":59,\"z\":900}]}", MediaType.APPLICATION_JSON));
        var result = new KartverketClient(builder.build()).peaksInArea(" Rogaland ", "NO").orElseThrow();
        assertEquals(1, result.size());
        assertEquals(900, result.getFirst().elevationMeters());
        assertEquals("Testtoppen", result.getFirst().location().name());
        server.verify();
    }
    @Test void unavailableEssentialDataDoesNotBecomeAnEmptySearch() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ws.geonorge.no/kommuneinfo/v1/fylker")).andRespond(withServerError());
        assertThrows(KartverketUnavailableException.class, () -> new KartverketClient(builder.build()).peaksInArea("Rogaland", "NO"));
    }
    @Test void heightsAreMatchedByCoordinatesAndMissingHeightsAreSkipped() throws Exception {
        var locations = List.of(new Location("A", 59, 6), new Location("B", 60, 7), new Location("C", 61, 8));
        var result = KartverketClient.withElevations(locations, JSON.readTree("""
            {"punkter":[{"x":7,"y":60,"z":1000},{"x":6,"y":59,"z":null},{"x":8,"y":61,"z":-9999}]}
            """));
        assertEquals(1, result.size()); assertEquals("B", result.getFirst().location().name());
    }
    @Test void placeParserDeduplicatesIdsAndRejectsMissingCoordinates() throws Exception {
        var result = KartverketClient.parsePlaces(JSON.readTree("""
            {"navn":[{"stedsnummer":1,"representasjonspunkt":{"nord":59,"øst":6},"stedsnavn":[{"skrivemåte":"A"}]},
              {"stedsnummer":1,"representasjonspunkt":{"nord":59,"øst":6},"stedsnavn":[{"skrivemåte":"A"}]},
              {"stedsnummer":2,"representasjonspunkt":{"nord":59},"stedsnavn":[{"skrivemåte":"B"}]}]}
            """));
        assertEquals(1, result.size()); assertEquals(6, result.getFirst().longitude());
    }
    @Test void areaTrailsAcceptTheRealWfsContentTypeAndUseExactBoundaries() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ws.geonorge.no/kommuneinfo/v1/fylker"))
            .andRespond(withSuccess("[{\"fylkesnavn\":\"Rogaland\",\"fylkesnummer\":\"11\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://ws.geonorge.no/kommuneinfo/v1/fylker/11?utkoordsys=4326"))
            .andRespond(withSuccess("{\"avgrensningsboks\":{\"coordinates\":[[[5,58],[7,58],[7,60],[5,60],[5,58]]]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://ws.geonorge.no/kommuneinfo/v1/fylker/11/omrade?utkoordsys=4326"))
            .andRespond(withSuccess("{\"omrade\":{\"type\":\"Polygon\",\"coordinates\":[[[5,58],[7,58],[7,60],[5,60],[5,58]]]}}", MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.ExpectedCount.times(4), requestTo(org.hamcrest.Matchers.containsString("wfs.turogfriluftsruter")))
            .andRespond(withSuccess().body("""
               <wfs:FeatureCollection xmlns:wfs="http://www.opengis.net/wfs/2.0" xmlns:a="https://example.org" xmlns:gml="http://www.opengis.net/gml/3.2">
                <wfs:member><a:Fotrute><a:rutenavn>Innenfor</a:rutenavn><gml:LineString><gml:posList>59 6 59.1 6.1</gml:posList></gml:LineString></a:Fotrute></wfs:member>
                <wfs:member><a:Fotrute><a:rutenavn>Utenfor</a:rutenavn><gml:LineString><gml:posList>59 8 59.1 8.1</gml:posList></gml:LineString></a:Fotrute></wfs:member>
               </wfs:FeatureCollection>
               """).header("Content-Type", "text/xml; subtype=gml/3.2.1;charset=UTF-8"));
        var result = new KartverketClient(builder.build()).trailsInArea("Rogaland", "NO").orElseThrow();
        assertEquals(1, result.size()); assertEquals("Innenfor", result.getFirst().name());
        server.verify();
    }
}
