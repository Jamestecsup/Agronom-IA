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
        FakeProvider spaceBunny = new FakeProvider("space-bunny", true);
        ChatOrchestrationService service = service(spaceBunny, new FakeProvider("qwen-local", true));

        String error = service.validate(new ChatContracts.ChatRequest("http://attacker.invalid", List.of(
                new ChatContracts.ChatMessage("user", "Hola"))));

        assertThat(error).isEqualTo("El modelo solicitado no está autorizado.");
        assertThat(spaceBunny.calls).hasValue(0);
    }

    @Test
    void forwardsTheConversationToOnlyTheSelectedProviderAndLoadsTheSharedSkill() throws Exception {
        FakeProvider spaceBunny = new FakeProvider("space-bunny", true);
        FakeProvider qwen = new FakeProvider("qwen-local", true);
        ChatOrchestrationService service = service(spaceBunny, qwen);
        ChatContracts.ChatRequest request = new ChatContracts.ChatRequest("qwen-local", List.of(
                new ChatContracts.ChatMessage("user", "¿Cómo se riega un helecho?"),
                new ChatContracts.ChatMessage("assistant", "Mantén el sustrato ligeramente húmedo."),
                new ChatContracts.ChatMessage("user", "¿Y en invierno?")));
        List<String> output = new ArrayList<>();

        service.stream(request, output::add, new ChatCancellation());

        assertThat(qwen.calls).hasValue(1);
        assertThat(spaceBunny.calls).hasValue(0);
        assertThat(qwen.messages).containsExactlyElementsOf(request.messages());
        assertThat(qwen.systemPrompt).contains("Space Bunny", "Responde primero a la solicitud más reciente")
                .doesNotStartWith("---");
        assertThat(output).containsExactly("Respuesta incremental.");
    }

    @Test
    void enforcesMessageCountAndRoleRulesAtTheApiBoundary() throws Exception {
        ChatOrchestrationService service = service(new FakeProvider("space-bunny", true), new FakeProvider("qwen-local", true));
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
        ChatOrchestrationService service = service(new FakeProvider("space-bunny", false),
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
                            .content("{\"providerId\":\"space-bunny\",\"messages\":[{\"role\":\"user\",\"content\":\"hola\"}]}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        } finally {
            executor.shutdown();
        }
    }

    private ChatOrchestrationService service(FakeProvider spaceBunny, FakeProvider qwen) throws Exception {
        BotanyInstructions instructions = new BotanyInstructions(new DefaultResourceLoader());
        return new ChatOrchestrationService(List.of(spaceBunny, qwen), instructions, properties);
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
                    isAvailable ? null : "Proveedor de prueba no disponible.");
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
