package com.impulselock.impulselock.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

/**
 * Maps a JSON column to a {@code Map<String, Object>} (used by {@code RuleConfig.params}).
 * A plain Jackson round-trip, not Hibernate's native JSON type, so behavior doesn't depend
 * on a specific Hibernate version's JSON support.
 */
@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON column value", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize JSON column value", e);
        }
    }
}
