package com.lava.swexpedited.batch.pickupmatch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@WireMockTest
class GooglePlacesClientTest {

    private static final String SEARCH_TEXT_PATH = "/v1/places:searchText";

    @Test
    void resolveDisplayName_placeFound_returnsDisplayNameAndSendsTextQuery(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(SEARCH_TEXT_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"places":[{"displayName":{"text":"Fort Hunter Liggett","languageCode":"en"}}]}
                                """)));

        GooglePlacesClient client =
                new GooglePlacesClient(searchTextRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Optional<String> result = client.resolveDisplayName("238 California Avenue, FT H LIGGETT, CA 93928");

        assertThat(result).contains("Fort Hunter Liggett");
        verify(postRequestedFor(urlPathEqualTo(SEARCH_TEXT_PATH)).withRequestBody(equalToJson("""
                        {"textQuery": "238 California Avenue, FT H LIGGETT, CA 93928"}
                        """)));
    }

    @Test
    void resolveDisplayName_noPlacesInResponse_returnsEmpty(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(SEARCH_TEXT_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"places":[]}
                                """)));

        GooglePlacesClient client =
                new GooglePlacesClient(searchTextRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Optional<String> result = client.resolveDisplayName("nonexistent place");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveDisplayName_placeWithoutDisplayName_returnsEmpty(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(SEARCH_TEXT_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"places":[{}]}
                                """)));

        GooglePlacesClient client =
                new GooglePlacesClient(searchTextRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Optional<String> result = client.resolveDisplayName("some address");

        assertThat(result).isEmpty();
    }

    @Test
    void resolveDisplayName_retriesOn5xxThenSucceeds(WireMockRuntimeInfo wireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo(SEARCH_TEXT_PATH))
                .inScenario("search-text-retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        stubFor(post(urlPathEqualTo(SEARCH_TEXT_PATH))
                .inScenario("search-text-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"places":[{"displayName":{"text":"Fort Hunter Liggett","languageCode":"en"}}]}
                                """)));

        GooglePlacesClient client =
                new GooglePlacesClient(searchTextRestClient(wireMockRuntimeInfo), Duration.ofMillis(10));

        Optional<String> result = client.resolveDisplayName("FT H LIGGETT, CA 93928");

        assertThat(result).contains("Fort Hunter Liggett");
        verify(2, postRequestedFor(urlPathEqualTo(SEARCH_TEXT_PATH)));
    }

    private RestClient searchTextRestClient(WireMockRuntimeInfo wireMockRuntimeInfo) {
        return RestClient.builder()
                .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
                .build();
    }
}
