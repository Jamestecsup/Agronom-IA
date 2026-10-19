package com.agronomia.backend.ai;

import java.util.List;

public final class ChatContracts {
    private ChatContracts() { }

    public record ChatImage(String fileName, String mimeType, byte[] data) { }
    public record ChatMessage(String role, String content, List<ChatImage> images) {
        public ChatMessage(String role, String content) { this(role, content, List.of()); }
    }
    public record ChatRequest(String providerId, List<ChatMessage> messages) { }
    public record ModelOption(String id, String name, String provider, String modelId,
                              boolean available, String unavailableReason, String welcomeMessage,
                              boolean supportsImages, String imageUnavailableReason) {
        public ModelOption(String id, String name, String provider, String modelId, boolean available,
                           String unavailableReason, String welcomeMessage) {
            this(id, name, provider, modelId, available, unavailableReason, welcomeMessage, false,
                    "El proveedor no ha confirmado compatibilidad con imágenes.");
        }
    }
    public record Delta(String text) { }
    public record Completion(String providerId) { }
    public record ChatError(String message) { }
}
