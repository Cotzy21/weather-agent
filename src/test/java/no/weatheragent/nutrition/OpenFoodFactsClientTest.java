package no.weatheragent.nutrition;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Mot en mock-server: vi ringer aldri de ekte serverne i tester. */
class OpenFoodFactsClientTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);
    private static final String URL = "https://world.openfoodfacts.org/api/v2/product/7622210449283.json"
            + "?fields=product_name,generic_name,brands,nutriments,serving_quantity";
    private static final String FOUND = """
            {"status":1,"product":{"product_name":"Prince","brands":"Mondelez","nutriments":{"energy-kcal_100g":467,
             "proteins_100g":6.3,"fat_100g":17,"carbohydrates_100g":69}}}
            """;

    private MockRestServiceServer server;

    private OpenFoodFactsClient client(int perMinute) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://world.openfoodfacts.org/api/v2")
                .defaultHeader(HttpHeaders.USER_AGENT, "weather-agent-test/1.0");
        server = MockRestServiceServer.bindTo(builder).build();
        return new OpenFoodFactsClient(builder.build(), CLOCK, perMinute);
    }

    @Test
    void looksUpTheProductWithAnIdentifyingUserAgentAndOnlyTheFieldsWeNeed() {
        OpenFoodFactsClient client = client(10);
        server.expect(requestTo(URL)).andExpect(method(GET))
                .andExpect(header(HttpHeaders.USER_AGENT, "weather-agent-test/1.0"))
                .andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        Optional<OpenFoodFactsProduct> product = client.lookup("7622210449283");

        assertEquals("Prince", product.orElseThrow().name());
        server.verify();
    }

    @Test
    void aUnknownBarcodeIsEmptyNotAnError() {
        OpenFoodFactsClient client = client(10);
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":0}", MediaType.APPLICATION_JSON));

        assertTrue(client.lookup("7622210449283").isEmpty());
    }

    @Test
    void a404FromTheApiIsAlsoJustNotFound() {
        OpenFoodFactsClient client = client(10);
        server.expect(requestTo(URL)).andRespond(withStatus(NOT_FOUND));

        assertTrue(client.lookup("7622210449283").isEmpty());
    }

    @Test
    void serverErrorsAreReportedAsUnavailableSoTheyAreNeverCachedAsNotFound() {
        OpenFoodFactsClient client = client(10);
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThrows(OpenFoodFactsClient.UnavailableException.class, () -> client.lookup("7622210449283"));
    }

    @Test
    void stopsCallingWhenTheGlobalPerMinuteLimitIsReached() {
        OpenFoodFactsClient client = client(2);
        server.expect(requestTo(URL)).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andRespond(withSuccess(FOUND, MediaType.APPLICATION_JSON));

        client.lookup("7622210449283");
        client.lookup("7622210449283");

        assertThrows(OpenFoodFactsClient.UnavailableException.class, () -> client.lookup("7622210449283"));
        server.verify(); // bare to eksterne kall ble gjort
    }
}
