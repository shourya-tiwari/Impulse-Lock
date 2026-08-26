package com.impulselock.impulselock.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impulselock.impulselock.model.TriggeredRuleEntry;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/** Maps {@code Transaction.triggeredRules} to/from its persisted JSON array column. */
@Converter
public class TriggeredRulesConverter implements AttributeConverter<List<TriggeredRuleEntry>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<TriggeredRuleEntry> attribute) {
        if (attribute == null) {
            return "[]";
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize triggered rules to JSON", e);
        }
    }

    @Override
    public List<TriggeredRuleEntry> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<List<TriggeredRuleEntry>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize triggered rules from JSON", e);
        }
    }
}
