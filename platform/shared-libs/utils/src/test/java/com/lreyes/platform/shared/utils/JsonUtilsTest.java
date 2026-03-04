package com.lreyes.platform.shared.utils;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilsTest {

    record Sample(String name, Instant ts) {}

    @Test
    void roundTrip_shouldSerializeAndDeserialize() {
        Instant now = Instant.parse("2025-01-15T10:30:00Z");
        Sample original = new Sample("test", now);

        String json = JsonUtils.toJson(original);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"test\""));
        assertTrue(json.contains("2025-01-15T10:30:00Z"));

        Sample restored = JsonUtils.fromJson(json, Sample.class);
        assertEquals("test", restored.name());
        assertEquals(now, restored.ts());
    }

    @Test
    void fromJsonSafe_shouldReturnEmptyOnInvalid() {
        Optional<Sample> result = JsonUtils.fromJsonSafe("not json", Sample.class);
        assertTrue(result.isEmpty());
    }

    @Test
    void fromJson_shouldThrowOnInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonUtils.fromJson("not json", Sample.class));
    }
}
