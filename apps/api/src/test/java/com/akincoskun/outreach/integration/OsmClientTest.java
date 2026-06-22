package com.akincoskun.outreach.integration;

import com.akincoskun.outreach.integration.CompanyDataSource.DiscoveredPlace;
import com.akincoskun.outreach.integration.CompanyDataSource.DiscoveryQuery;
import com.akincoskun.outreach.integration.OsmClient.OverpassElement;
import com.akincoskun.outreach.integration.OsmClient.OverpassResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OsmClientTest {

    private final OsmClient client =
        new OsmClient("https://overpass-api.de/api/interpreter", 0L, 25);

    @Test
    void parsesNodeAndWayElementsIntoDiscoveredPlaces() throws Exception {
        // A representative Overpass /interpreter JSON response.
        String json = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 1,
                  "lat": 41.0,
                  "lon": 29.0,
                  "tags": {
                    "name": "Acme Apartman Yönetimi",
                    "office": "property_management",
                    "website": "https://www.acme-yonetim.com",
                    "phone": "+90 212 000 0000",
                    "addr:street": "Bağdat Caddesi",
                    "addr:housenumber": "12",
                    "addr:city": "İstanbul"
                  }
                },
                {
                  "type": "way",
                  "id": 2,
                  "center": { "lat": 41.1, "lon": 29.1 },
                  "tags": {
                    "name": "Beta Yönetim",
                    "office": "property_management",
                    "contact:website": "http://beta-yonetim.com.tr",
                    "contact:phone": "0212 111 1111"
                  }
                }
              ]
            }
            """;

        OverpassResponse response = new ObjectMapper().readValue(json, OverpassResponse.class);
        List<DiscoveredPlace> places = client.parse(response);

        assertThat(places).hasSize(2);

        DiscoveredPlace first = places.get(0);
        assertThat(first.osmId()).isEqualTo("node/1");
        assertThat(first.name()).isEqualTo("Acme Apartman Yönetimi");
        assertThat(first.website()).isEqualTo("https://www.acme-yonetim.com");
        assertThat(first.phone()).isEqualTo("+90 212 000 0000");
        assertThat(first.address()).isEqualTo("Bağdat Caddesi, 12, İstanbul");

        DiscoveredPlace second = places.get(1);
        assertThat(second.osmId()).isEqualTo("way/2");
        assertThat(second.name()).isEqualTo("Beta Yönetim");
        // Falls back to contact:* tags.
        assertThat(second.website()).isEqualTo("http://beta-yonetim.com.tr");
        assertThat(second.phone()).isEqualTo("0212 111 1111");
        assertThat(second.address()).isNull();
    }

    @Test
    void skipsElementsWithoutNameOrTags() {
        OverpassResponse response = new OverpassResponse(List.of(
            new OverpassElement("node", 1, null),
            new OverpassElement("node", 2, Map.of("office", "property_management")),
            new OverpassElement("node", 3, Map.of("name", "  ")),
            new OverpassElement("node", 4, Map.of("name", "Keep Me"))
        ));

        List<DiscoveredPlace> places = client.parse(response);

        assertThat(places).hasSize(1);
        assertThat(places.get(0).name()).isEqualTo("Keep Me");
    }

    @Test
    void handlesNullOrEmptyResponse() {
        assertThat(client.parse(null)).isEmpty();
        assertThat(client.parse(new OverpassResponse(null))).isEmpty();
    }

    @Test
    void buildsOverpassQlWithMappedIndustryTagAndCityArea() {
        String ql = client.buildQuery(new DiscoveryQuery("property_management", "TR", "İstanbul", null));

        assertThat(ql)
            .contains("[out:json][timeout:25];")
            .contains("area[\"name\"=\"İstanbul\"]->.searchArea;")
            .contains("node[\"office\"=\"property_management\"](area.searchArea);")
            .contains("way[\"office\"=\"property_management\"](area.searchArea);")
            .contains("out body center;");
    }

    @Test
    void fallsBackToCountryAreaAndDefaultTagForUnknownIndustry() {
        String ql = client.buildQuery(new DiscoveryQuery("mystery_sector", "NL", null, null));

        assertThat(ql)
            .contains("area[\"ISO3166-1\"=\"NL\"]->.searchArea;")
            .contains("node[\"office\"=\"company\"](area.searchArea);");
    }
}
