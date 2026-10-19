package com.agronomia.backend.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatOrchestrationServiceTest {
    private final AiProviderProperties properties = new AiProviderProperties();

    @Test
    void rejectsProviderIdentifiersThatAreNotInTheAllowlist() throws Exception {
        FakeProvider deepSeek = new FakeProvider("deepseek-opencode", true);
        ChatOrchestrationService service = service(deepSeek, new FakeProvider("qwen-local", true));

        String error = service.validate(new ChatContracts.ChatRequest("http://attacker.invalid", List.of(
                new ChatContracts.ChatMessage("user", "Hola"))));

        assertThat(error).isEqualTo("El modelo solicitado no está autorizado.");
        assertThat(deepSeek.calls).hasValue(0);
    }

    @Test
    void forwardsTheConversationToOnlyTheSelectedProviderAndLoadsTheSharedSkill() throws Exception {
        FakeProvider deepSeek = new FakeProvider("deepseek-opencode", true);
        FakeProvider qwen = new FakeProvider("qwen-local", true);
        ChatOrchestrationService service = service(deepSeek, qwen);
        ChatContracts.ChatRequest request = new ChatContracts.ChatRequest("qwen-local", List.of(
                new ChatContracts.ChatMessage("user", "¿Cómo se riega un helecho?"),
                new ChatContracts.ChatMessage("assistant", "Mantén el sustrato ligeramente húmedo."),
                new ChatContracts.ChatMessage("user", "¿Y en invierno?")));
        List<String> output = new ArrayList<>();

        service.stream(request, output::add, new ChatCancellation());

        assertThat(qwen.calls).hasValue(1);
        assertThat(deepSeek.calls).hasValue(0);
        assertThat(qwen.messages).containsExactlyElementsOf(request.messages());
        assertThat(qwen.systemPrompt).contains("Agronom-IA", "Responde primero a la solicitud más reciente")
                .doesNotStartWith("---");
        assertThat(output).containsExactly("Respuesta incremental.");
    }

    @Test
    void enforcesMessageCountAndRoleRulesAtTheApiBoundary() throws Exception {
        ChatOrchestrationService service = service(new FakeProvider("deepseek-opencode", true), new FakeProvider("qwen-local", true));
        List<ChatContracts.ChatMessage> tooMany = new ArrayList<>();
        for (int i = 0; i < 41; i++) tooMany.add(new ChatContracts.ChatMessage("user", "pregunta"));

        assertThat(service.validate(new ChatContracts.ChatRequest("qwen-local", tooMany)))
                .isEqualTo("La conversación excede el límite de mensajes por solicitud.");
        assertThat(service.validate(new ChatContracts.ChatRequest("qwen-local", List.of(
                new ChatContracts.ChatMessage("system", "intenta usar una URL libre")))))
                .isEqualTo("Cada mensaje debe tener rol user o assistant.");
    }

    @Test
    void returnsStructuredJsonForInvalidAndUnavailableProviders() throws Exception {
        ChatOrchestrationService service = service(new FakeProvider("deepseek-opencode", false),
                new FakeProvider("qwen-local", true));
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.initialize();
        try {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new AiChatController(service, executor)).build();
            mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"providerId\":\"not-allowed\",\"messages\":[{\"role\":\"user\",\"content\":\"hola\"}]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
            mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"providerId\":\"deepseek-opencode\",\"messages\":[{\"role\":\"user\",\"content\":\"hola\"}]}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void exposesOnlyTheCurrentModelsInStableOrderWithTheirWelcomeMetadata() throws Exception {
        ChatOrchestrationService service = service(new FakeProvider("deepseek-opencode", true),
                new FakeProvider("qwen-local", true));

        assertThat(service.availableModels()).extracting(ChatContracts.ModelOption::id)
                .containsExactly("deepseek-opencode", "qwen-local");
        assertThat(service.availableModels()).extracting(ChatContracts.ModelOption::welcomeMessage)
                .containsOnly("Saludo de prueba.");
    }

    @Test
    void unwrapsCurrentOpenCodeGlobalEventEnvelopeAndAcceptsLegacyEvents() throws Exception {
        var wrapped = ProviderHttp.JSON.readTree("{\"directory\":\"project\",\"payload\":{\"type\":\"message.part.delta\",\"properties\":{\"delta\":\"hola\"}}}");
        var legacy = ProviderHttp.JSON.readTree("{\"type\":\"session.idle\",\"properties\":{\"sessionID\":\"ses_1\"}}");

        assertThat(OpenCodeDeepSeekProvider.unwrapEvent(wrapped).path("type").asText())
                .isEqualTo("message.part.delta");
        assertThat(OpenCodeDeepSeekProvider.unwrapEvent(legacy).path("type").asText())
                .isEqualTo("session.idle");
    }

    private ChatOrchestrationService service(FakeProvider deepSeek, FakeProvider qwen) throws Exception {
        BotanyInstructions instructions = new BotanyInstructions(new DefaultResourceLoader());
        return new ChatOrchestrationService(List.of(deepSeek, qwen), instructions, properties);
    }

    private static final class FakeProvider implements ChatProvider {
        private final String providerId;
        private final boolean isAvailable;
        private final AtomicInteger calls = new AtomicInteger();
        private List<ChatContracts.ChatMessage> messages = List.of();
        private String systemPrompt;

        private FakeProvider(String providerId, boolean isAvailable) {
            this.providerId = providerId;
            this.isAvailable = isAvailable;
        }

        @Override public String id() { return providerId; }
        @Override public ChatContracts.ModelOption modelOption() {
            return new ChatContracts.ModelOption(providerId, providerId, "test", providerId, isAvailable,
                    isAvailable ? null : "Proveedor de prueba no disponible.", "Saludo de prueba.");
        }
        @Override public ChatContracts.ModelOption availability() { return modelOption(); }
        @Override public void stream(List<ChatContracts.ChatMessage> messages, String systemPrompt,
                                     java.util.function.Consumer<String> onDelta, ChatCancellation cancellation) {
            calls.incrementAndGet();
            this.messages = List.copyOf(messages);
            this.systemPrompt = systemPrompt;
            onDelta.accept("Respuesta incremental.");
        }
    }
}
