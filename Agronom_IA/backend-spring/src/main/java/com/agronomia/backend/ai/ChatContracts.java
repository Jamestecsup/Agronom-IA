package com.agronomia.backend.ai;

import java.util.List;

public final class ChatContracts {
    private ChatContracts() { }

    public record ChatMessage(String role, String content) { }
    public record ChatRequest(String providerId, List<ChatMessage> messages) { }
    public record ModelOption(String id, String name, String provider, String modelId,
                              boolean available, String unavailableReason) { }
    public record Delta(String text) { }
    public record Completion(String providerId) { }
    public record ChatError(String message) { }
}
