package com.akincoskun.outreach.integration;

import com.akincoskun.outreach.integration.ApifyClient.ApifyPlace;
import com.akincoskun.outreach.integration.CompanyDataSource.DiscoveredPlace;
import com.akincoskun.outreach.integration.CompanyDataSource.DiscoveryQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApifyClientTest {

    private final ApifyClient client =
        new ApifyClient("test-token", "compass~crawler-google-places", 120, 20);

    @Test
    void buildsActorInputWithCostSafeDefaults() {
        DiscoveryQuery query =
            new DiscoveryQuery("property_management", "TR", "İstanbul", "apartman yönetimi");

        Map<String, Object> input = client.buildInput(query);

        // Search string prefers the keyword over the raw industry slug.
        assertThat(input.get("searchStringsArray")).isEqualTo(List.of("apartman yönetimi İstanbul"));
        assertThat(input.get("locationQuery")).isEqualTo("İstanbul, Turkey");
        assertThat(input.get("maxCrawledPlacesPerSearch")).isEqualTo(20);
        // Cost guards: paid enrichments must be off to preserve the free tier.
        assertThat(input.get("maxImages")).isEqualTo(0);
        assertThat(input.get("includeWebResults")).isEqualTo(false);
        assertThat(input.get("scrapeReviewsPersonalData")).isEqualTo(false);
        assertThat(input.get("scrapeContacts")).isEqualTo(false);
    }

    @Test
    void searchStringFallsBackToIndustryWhenNoKeyword() {
        assertThat(client.searchString(new DiscoveryQuery("restaurant", "TR", "Ankara", null)))
            .isEqualTo("restaurant Ankara");
        assertThat(client.searchString(new DiscoveryQuery("saas", "US", null, "b2b startup")))
            .isEqualTo("b2b startup");
    }

    @Test
    void locationQueryMapsCountryCodeToFullName() {
        assertThat(client.locationQuery(new DiscoveryQuery("x", "US", null, null)))
            .isEqualTo("United States");
        assertThat(client.locationQuery(new DiscoveryQuery("x", "GB", "London", null)))
            .isEqualTo("London, United Kingdom");
        assertThat(ApifyClient.countryName("TR")).isEqualTo("Turkey");
        assertThat(ApifyClient.countryName("DE")).isEqualTo("Germany");
        // Unknown code passes through uppercased rather than failing.
        assertThat(ApifyClient.countryName("nl")).isEqualTo("NL");
    }

    @Test
    void parsesItemsAndMapsGoogleMapsUrlToExternalId() {
        List<ApifyPlace> items = List.of(
            new ApifyPlace("Acme Yönetim", "https://acme-yonetim.com", "+90 212 000 0000",
                "Bağdat Cd, İstanbul", "İstanbul", "https://maps.google.com/?cid=123", "ChIJ_acme"),
            // Missing website is kept (filtered later); literal "undefined" → null.
            new ApifyPlace("Beta Yönetim", "undefined", null, null, "İstanbul",
                "https://maps.google.com/?cid=456", "ChIJ_beta")
        );

        List<DiscoveredPlace> places = client.parse(items);

        assertThat(places).hasSize(2);

        DiscoveredPlace first = places.get(0);
        assertThat(first.osmId()).isEqualTo("https://maps.google.com/?cid=123");
        assertThat(first.name()).isEqualTo("Acme Yönetim");
        assertThat(first.website()).isEqualTo("https://acme-yonetim.com");
        assertThat(first.address()).isEqualTo("Bağdat Cd, İstanbul");
        assertThat(first.phone()).isEqualTo("+90 212 000 0000");

        DiscoveredPlace second = places.get(1);
        assertThat(second.osmId()).isEqualTo("https://maps.google.com/?cid=456");
        assertThat(second.website()).isNull();   // "undefined" normalized away
        assertThat(second.phone()).isNull();
    }

    @Test
    void skipsItemsWithoutTitle() {
        List<ApifyPlace> items = List.of(
            new ApifyPlace(null, "https://x.com", null, null, null, "url1", "id1"),
            new ApifyPlace("  ", "https://y.com", null, null, null, "url2", "id2"),
            new ApifyPlace("Keep Me", "https://z.com", null, null, null, "url3", "id3")
        );

        List<DiscoveredPlace> places = client.parse(items);

        assertThat(places).hasSize(1);
        assertThat(places.get(0).name()).isEqualTo("Keep Me");
    }

    @Test
    void handlesNullItemList() {
        assertThat(client.parse(null)).isEmpty();
    }

    @Test
    void deserializesRealActorItemShape() throws Exception {
        // A trimmed compass/crawler-google-places dataset item — unknown fields ignored.
        String json = """
            {
              "title": "Acme Apartman Yönetimi",
              "website": "https://acme-yonetim.com",
              "phone": "+90 212 000 0000",
              "address": "Bağdat Cd 12, İstanbul",
              "city": "İstanbul",
              "url": "https://www.google.com/maps/place/?q=place_id:ChIJ_acme",
              "placeId": "ChIJ_acme",
              "categoryName": "Property management company",
              "totalScore": 4.5
            }""";

        ApifyPlace place = new ObjectMapper().readValue(json, ApifyPlace.class);

        assertThat(place.title()).isEqualTo("Acme Apartman Yönetimi");
        assertThat(place.website()).isEqualTo("https://acme-yonetim.com");
        assertThat(place.placeId()).isEqualTo("ChIJ_acme");
    }

    @Test
    void blankTokenShortCircuitsToEmptyWithoutNetworkCall() {
        ApifyClient noToken = new ApifyClient("", "actor", 120, 20);
        assertThat(noToken.search(new DiscoveryQuery("x", "TR", "İstanbul", "y"))).isEmpty();
    }
}
