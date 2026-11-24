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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlantCardsTest {
    private static final List<String> KEYS = List.of(
            "description", "light", "watering", "soil", "climate", "flowering", "uses", "care");
    private final ObjectMapper json = new ObjectMapper();
    private final Stub ai = new Stub();
    private final PlantCards cards = new PlantCards(json, ai, false, "estimula las defensas");
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

    @Test
    void parsesBatchesAndDiscardsMalformedEntriesWithoutLosingValidOnes() throws Exception {
        ai.responses.add(full());
        ai.enrichments.put("glossary", json.readTree("""
                {"description":[{"word":"rizoma","meaning":"Tallo subterráneo."},
                {"word":"raíz"},{"meaning":"Sin palabra"},{"word":" ","meaning":"Explicación."}],
                "light":[],"soil":{},"":[{"word":"suelto","meaning":"Sin categoría."}]}
                """));
        ai.enrichments.put("materials", json.readTree("""
                {"watering":[{"name":"Regadera","detail":"Dirige el agua a la base."},
                {"name":"Té","detail":"Es seguro para consumir."}],"soil":[]}
                """));

        JsonNode sections = generate().path("sections");
        assertThat(sections.path(0).path("terms")).hasSize(1);
        assertThat(sections.path(0).path("terms").path(0).path("word").asText()).isEqualTo("rizoma");
        assertThat(sections.path(1).path("terms")).isEmpty();
        assertThat(sections.path(3).path("terms")).isEmpty();
        assertThat(sections.path(2).path("materials")).hasSize(1);
        assertThat(sections.path(3).path("materials")).isEmpty();
        assertThat(ai.prompts.get("glossary")).contains("VARIAS categorías", "\"description\":[{\"word\":", "\"meaning\":");
        assertThat(ai.prompts.get("materials")).contains("VARIAS categorías", "\"watering\":[{\"name\":", "\"detail\":");
    }

    @Test
    void rejectsFlatEnrichmentWhenMultipleCategoriesWereRequested() throws Exception {
        ai.responses.add(full());
        ai.enrichments.put("glossary", json.readTree("""
                {"terms":[{"word":"rizoma","meaning":"Tallo subterráneo."}]}
                """));
        ai.enrichments.put("materials", json.readTree("""
                {"items":[{"name":"Regadera","detail":"Para regar."}]}
                """));

        for (JsonNode section : generate().path("sections")) {
            assertThat(section.path("terms")).isEmpty();
            assertThat(section.path("materials")).isEmpty();
        }
    }

    @Test
    void recoversFlatEnrichmentWhenOnlyOneCategoryWasSent() throws Exception {
        ObjectNode response = json.createObjectNode();
        KEYS.forEach(key -> response.put(key, "Se recomienda su uso medicinal."));
        response.put("light", "Necesita luz directa.");
        ai.responses.add(response);
        ai.enrichments.put("glossary", json.readTree("""
                {"terms":[{"word":"directa","meaning":"Luz sin sombra."}]}
                """));
        ai.enrichments.put("materials", json.readTree("""
                {"items":[{"name":"Malla","detail":"Permite ajustar la exposición."}]}
                """));

        JsonNode sections = generate().path("sections");
        assertThat(sections.path(1).path("terms").path(0).path("word").asText()).isEqualTo("directa");
        assertThat(sections.path(1).path("materials").path(0).path("name").asText()).isEqualTo("Malla");
        assertThat(sections.path(0).path("terms")).isEmpty();
        assertThat(sections.path(0).path("body").asText()).contains("No hay información", SensitivePlantClaims.NOTICE);
    }

    @Test
    void filtersSensitiveSentencesInEveryCategoryAndAddsOneNoticePerAffectedSection() {
        var response = full();
        KEYS.forEach(key -> response.put(key, """
                Procura mantener el suelo húmedo. La planta NO ES TÓXICA. Puede comerse sin preparación.
                Sirve como remedio. Esta planta ESTIMULA LAS DEFENSAS. Colócala a pleno sol.
                """));
        response.put("care", "Conserva la humedad del sustrato.");
        ai.responses.add(response);

        for (JsonNode section : generate().path("sections")) {
            String body = section.path("body").asText();
            if (section.path("key").asText().equals("care")) {
                assertThat(body).contains("Conserva la humedad").doesNotContain(SensitivePlantClaims.NOTICE);
            } else {
                assertThat(body).contains("Procura mantener", "pleno sol")
                        .containsOnlyOnce(SensitivePlantClaims.NOTICE)
                        .doesNotContain("NO ES TÓXICA", "Puede comerse", "remedio", "ESTIMULA LAS DEFENSAS");
            }
        }
        assertThat(new SensitivePlantClaims("").filter("Es comestible. Es ornamental. Dicen que es curativa.").removed())
                .isEqualTo(2);
    }

    @Test
    void explicitOptInKeepsCandidateHints() throws Exception {
        ai.enrichments.put("candidate-hints", json.readTree("""
                {"Helianthus annuus":{"hint":"Observa las hojas.","terms":[{"word":"hoja","meaning":"Parte de la planta."}]}}
                """));
        var withHints = new PlantCards(json, ai, true, "");
        try {
            JsonNode candidate = withHints.candidates(List.of(species), true,
                    AiClient.Deadline.after(Duration.ofSeconds(30))).path(0);
            assertThat(candidate.path("hint").asText()).isEqualTo("Observa las hojas.");
            assertThat(candidate.path("terms")).hasSize(1);
        } finally {
            withHints.stop();
        }
    }

    @Test
    void enrichmentsUseAuxiliaryRouteInParallelWithSeventyFiveSecondCap() {
        ai.parallel = new CountDownLatch(2);
        cards.enrichments("Helianthus annuus", full(), AiClient.Deadline.after(Duration.ofSeconds(150)), false);
        assertThat(ai.auxiliaryDeadlines).containsOnlyKeys("glossary", "materials");
        assertThat(ai.auxiliaryDeadlines.values()).allSatisfy(nanos ->
                assertThat(nanos).isBetween(Duration.ofSeconds(70).toNanos(), Duration.ofSeconds(75).toNanos()));
        assertThat(ai.auxiliaryRetries.values()).containsExactlyInAnyOrder(false, false);
    }

    @Test
    void capsToRemainingGlobalTimeAndSkipsBelowFiveSeconds() {
        cards.enrichments("Helianthus annuus", full(), AiClient.Deadline.after(Duration.ofSeconds(12)), true);
        assertThat(ai.auxiliaryDeadlines).containsOnlyKeys("glossary", "materials");
        assertThat(ai.auxiliaryDeadlines.values()).allSatisfy(nanos ->
                assertThat(nanos).isBetween(Duration.ofSeconds(5).toNanos(), Duration.ofSeconds(12).toNanos()));
        assertThat(ai.auxiliaryRetries.values()).containsExactlyInAnyOrder(true, true);
        ai.auxiliaryDeadlines.clear();
        cards.enrichments("Helianthus annuus", full(), AiClient.Deadline.after(Duration.ofSeconds(4)), true);
        assertThat(ai.auxiliaryDeadlines).isEmpty();
    }

    @Test
    void capsValidItemsPerCategory() {
        var glossary = json.createObjectNode();
        var materials = json.createObjectNode();
        var terms = glossary.putArray("light");
        var items = materials.putArray("light");
        for (int i = 0; i < 7; i++) {
            terms.addObject().put("word", "Término " + i).put("meaning", "Explicación.");
            items.addObject().put("name", "Material " + i).put("detail", "Instrucción.");
        }
        ai.enrichments.put("glossary", glossary);
        ai.enrichments.put("materials", materials);
        var result = cards.enrichments("Helianthus annuus", full(), AiClient.Deadline.after(Duration.ofSeconds(150)), true);
        assertThat(result.glossary().path("light")).hasSize(5);
        assertThat(result.materials().path("light")).hasSize(4);
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
        final Map<String, JsonNode> enrichments = new ConcurrentHashMap<>();
        final Map<String, String> prompts = new ConcurrentHashMap<>();
        final Map<String, Long> auxiliaryDeadlines = new ConcurrentHashMap<>();
        final Map<String, Boolean> auxiliaryRetries = new ConcurrentHashMap<>();
        CountDownLatch parallel;

        @Override
        public JsonResponse auxiliaryJsonWithin(String step, List<Map<String, Object>> messages,
                                                Deadline deadline, boolean retry) {
            auxiliaryDeadlines.put(step, deadline.remainingNanos());
            auxiliaryRetries.put(step, retry);
            if (parallel != null) {
                parallel.countDown();
                try {
                    assertThat(parallel.await(3, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(error);
                }
            }
            return AiClient.super.auxiliaryJsonWithin(step, messages, deadline, retry);
        }

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
            prompts.put(step, messages.get(0).toString());
            return enrichments.getOrDefault(step, json.createObjectNode());
        }
    }
}
