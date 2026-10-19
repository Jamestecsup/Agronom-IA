package com.agronomia.backend.ai;

import java.util.List;
import java.util.function.Consumer;

public interface ChatProvider {
    String id();
    ChatContracts.ModelOption modelOption();
    ChatContracts.ModelOption availability();
    void stream(List<ChatContracts.ChatMessage> messages, String systemPrompt,
                Consumer<String> onDelta, ChatCancellation cancellation);
}
