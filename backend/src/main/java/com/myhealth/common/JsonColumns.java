package com.myhealth.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Centralises the boilerplate for (de)serializing JSONB column payloads, so each entity
 * service doesn't repeat the same try/catch wrapping around Jackson.
 */
public final class JsonColumns {
    private JsonColumns() {
    }

    public static String write(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize JSON column", ex);
        }
    }

    public static <T> T read(ObjectMapper mapper, String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize JSON column", ex);
        }
    }
}
