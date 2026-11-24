package com.agronomia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface AiClient {
    String reply(String step, List<Map<String, Object>> messages, boolean vision);
    JsonNode json(String step, List<Map<String, Object>> messages, boolean vision);

    static Map<String, Object> message(String role, String text) {
        return Map.of("role", role, "content", List.of(Map.of("type", "text", "text", text)));
    }

    final class Failure extends RuntimeException {
        private final boolean visionUnsupported;
        public Failure(String message, boolean visionUnsupported) {
            super(message);
            this.visionUnsupported = visionUnsupported;
        }
        public boolean visionUnsupported() { return visionUnsupported; }
    }
}
