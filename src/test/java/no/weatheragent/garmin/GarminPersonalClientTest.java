package no.weatheragent.garmin;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GarminPersonalClientTest {
    @Test
    void exchangesKeyForTokenAndFetchesActivitySummaries() {
        var builder = RestClient.builder().baseUrl(GarminPersonalClient.BASE_URL);
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new GarminPersonalClient(builder.build());

        server.expect(requestTo(GarminPersonalClient.BASE_URL + GarminPersonalClient.TOKEN_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "ApiKey test-key"))
                .andRespond(withSuccess("test.payload.signature", MediaType.TEXT_PLAIN));
        server.expect(requestTo(GarminPersonalClient.BASE_URL + GarminPersonalClient.ACTIVITY_PATH + "?start=0&limit=20"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test.payload.signature"))
                .andRespond(withSuccess("""
                        [{"activityId":7654321,"activityName":"Morning run","startTimeLocal":"2026-11-05 08:00:00",
                          "duration":1800,"distance":5000,"startLatitude":60.0}]
                        """, MediaType.APPLICATION_JSON));

        assertEquals(new GarminPersonalClient.Activity("7654321", "Morning run", "2026-11-05 08:00:00",
                null, 1800.0, 5000.0, null, null, null, null, null, null, null), client.recentActivities("test-key").getFirst());
        server.verify();
    }
}
