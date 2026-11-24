package com.agronomia.backend.user;

import com.agronomia.backend.ai.AiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlantCardsTest {
    private static final List<String> KEYS = List.of(
            "description", "light", "watering", "soil", "climate", "flowering", "uses", "care");
    private final ObjectMapper json = new ObjectMapper();
    private final Stub ai = new Stub();
    private final PlantCards cards = new PlantCards(json, ai);
    private final JsonNode species = json.createObjectNode().set("species",
            json.createObjectNode().put("scientificNameWithoutAuthor", "Helianthus annuus"));

    @AfterEach
    void close() { cards.stop(); }

    @Test
    void returnsEightSectionsForCompleteText() {
        ai.responses.add(full());
        var result = generate();
        assertThat(result.path("sections").size()).isEqualTo(8);
        assertThat(result.path("sections").path(0).path("body").asText()).contains("Guía concreta");
        assertThat(ai.categories).hasSize(1);
    }

    @Test
    void preservesFallbackForFewerThanFourMissingFields() {
        var partial = full();
        partial.remove(List.of("light", "soil", "care"));
        ai.responses.add(partial);
        var result = generate();
        assertThat(result.path("sections").path(1).path("body").asText()).contains("No hay información");
        assertThat(ai.categories).hasSize(1);
    }

    @Test
    void oneStricterRetryCanRecoverMissingFields() {
        ai.responses.add(json.createObjectNode().put("description", "Descripción parcial."));
        ai.responses.add(full());
        assertThat(generate().path("sections").path(7).path("body").asText()).contains("Guía concreta");
        assertThat(ai.categories).hasSize(2);
        assertThat(ai.categories.get(1)).contains("escribe siempre la mejor guía general");
    }

    @Test
    void allUnknownAfterRetryReturnsInsufficient() {
        ObjectNode unknown = json.createObjectNode();
        KEYS.forEach(key -> unknown.put(key, "unknown"));
        ai.responses.add(unknown);
        ai.responses.add(unknown);
        assertThatThrownBy(this::generate).isInstanceOf(PlantCards.NoPlantInformation.class);
        assertThat(ai.categories).hasSize(2);
    }

    @Test
    void acceptsSpanishAliasesCapitalizationAndNestedValues() {
        var response = json.createObjectNode();
        response.put("DESCRIPCIÓN", "Una planta.");
        response.putArray("LUZ").add("Luz plena.").add("Protección si arde el sol.");
        response.putObject("RIEGO").put("cuidado", "Ajustar según humedad.");
        for (String key : List.of("SUELO", "CLIMA", "FLORACIÓN", "USOS", "CUIDADOS")) {
            response.put(key, "Guía orientativa.");
        }
        ai.responses.add(response);
        var sections = generate().path("sections");
        assertThat(sections.path(1).path("body").asText()).contains("Luz plena.", "Protección");
        assertThat(sections.path(2).path("body").asText()).contains("Ajustar según humedad.");
        assertThat(ai.categories).hasSize(1);
    }

    private ObjectNode full() {
        var result = json.createObjectNode();
        KEYS.forEach(key -> result.put(key, "Guía concreta para " + key + "."));
        return result;
    }

    private ObjectNode generate() {
        return cards.generate(species, AiClient.Deadline.after(Duration.ofSeconds(150)));
    }

    private class Stub implements AiClient {
        final Queue<JsonNode> responses = new ArrayDeque<>();
        final List<String> categories = new ArrayList<>();

        @Override
        public String reply(String step, List<Map<String, Object>> messages, boolean vision) {
            throw new AssertionError("No se esperaba una llamada de chat.");
        }

        @Override
        public JsonNode json(String step, List<Map<String, Object>> messages, boolean vision) {
            if (step.equals("categories")) {
                categories.add(messages.get(0).toString());
                return responses.remove();
            }
            return json.createObjectNode();
        }
    }
}
