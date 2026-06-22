package com.akincoskun.outreach.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link DiscoverySource} to its lowercase DB code ({@code osm}/{@code apify})
 * instead of the enum name, so the {@code discovery_filters.source} column stays
 * the human-friendly value the V23 migration seeds.
 */
@Converter
public class DiscoverySourceConverter implements AttributeConverter<DiscoverySource, String> {

    @Override
    public String convertToDatabaseColumn(DiscoverySource attribute) {
        return (attribute == null ? DiscoverySource.OSM : attribute).code();
    }

    @Override
    public DiscoverySource convertToEntityAttribute(String dbData) {
        return DiscoverySource.fromCode(dbData);
    }
}
