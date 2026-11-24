package com.agronomia.backend.user;

import com.agronomia.backend.ai.AiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class PlantCards {
    private static final List<String> KEYS = List.of(
            "description", "light", "watering", "soil", "climate", "flowering", "uses", "care");
    private static final List<String> TITLES = List.of(
            "Descripción", "Luz", "Riego", "Suelo", "Clima", "Floración", "Usos", "Cuidados");
    private static final Set<String> MATERIAL_KEYS = Set.of("light", "watering", "soil", "climate", "care");
    private static final Set<String> UNKNOWN = Set.of("unknown", "desconocido", "desconocida",
            "no disponible", "n/a", "no se conoce", "sin informacion", "sin información", "no sé", "no se");
    private static final String FALLBACK = "No hay información fiable para esta categoría.";
    private static final String CONDITIONS = " El cuidado puede variar según el clima y la variedad.";
    public static final String INSUFFICIENT = "Identificamos la especie, pero no encontramos información suficiente sobre sus cuidados.";

    private final ObjectMapper mapper;
    private final AiClient ai;
    private final ExecutorService enrichment = Executors.newFixedThreadPool(4);

    public PlantCards(ObjectMapper mapper, AiClient ai) {
        this.mapper = mapper;
        this.ai = ai;
    }

    @jakarta.annotation.PreDestroy
    void stop() { enrichment.shutdownNow(); }

    public List<JsonNode> ranked(JsonNode raw) {
        var results = new ArrayList<JsonNode>();
        raw.path("results").forEach(results::add);
        results.sort(Comparator.comparingDouble((JsonNode row) -> row.path("score").asDouble()).reversed());
        return results;
    }

    public ObjectNode header(JsonNode row) {
        JsonNode species = row.path("species");
        return mapper.createObjectNode()
                .put("common_name", species.path("commonNames").path(0).asText(""))
                .put("scientific_name", scientific(row))
                .put("authorship", species.path("scientificNameAuthorship").asText(""))
                .put("family", species.path("family").path("scientificNameWithoutAuthor").asText(""))
                .put("confidence", row.path("score").asDouble());
    }

    public String scientific(JsonNode row) {
        JsonNode species = row.path("species");
        return species.path("scientificNameWithoutAuthor").asText(species.path("scientificName").asText(""));
    }

    public ArrayNode candidates(List<JsonNode> rows, boolean hints) {
        ArrayNode output = mapper.createArrayNode();
        for (JsonNode row : rows) {
            ObjectNode item = header(row);
            item.remove(List.of("common_name", "authorship"));
            item.set("common_names", array(row.path("species").path("commonNames")));
            output.add(item);
        }
        if (!hints) return output;
        JsonNode details = optional("candidate-hints", skill("candidate-hints") + """
                Devuelve un objeto con una clave por nombre científico y valores
                {"hint":"pista visual","terms":[{"word":"término","meaning":"definición"}]}.
                """, output.toString());
        for (JsonNode candidate : output) {
            ObjectNode item = (ObjectNode) candidate;
            JsonNode found = details.path(item.path("scientific_name").asText());
            item.put("hint", limit(clean(found.path("hint")), 800));
            item.set("terms", sanitized(found.path("terms"), "word", "meaning", 80, 300, 6));
        }
        return output;
    }

    public ObjectNode generate(JsonNode species) {
        ObjectNode plant = header(species);
        String fact = "Ficha de Pl@ntNet:\n" + plant + "\nReferencias taxonómicas:\n" + species.path("species");
        String refined = optional("refine", skill("refine-prompt"), fact).path("prompt").asText("");
        String categoryRules = KEYS.stream().map(key -> "### " + key + "\n" + skill("categories/" + key))
                .collect(Collectors.joining("\n"));
        JsonNode categories = ask("categories", """
                Genera una ficha botánica en español. Devuelve un objeto JSON con estas claves
                y un texto por clave. Devuelve «unknown» si no conoces la categoría.
                """ + KEYS + "\n" + categoryRules, fact + "\n" + refined);
        ObjectNode texts = mapper.createObjectNode();
        ObjectNode materialTexts = mapper.createObjectNode();
        for (String key : KEYS) {
            String text = clean(categories.path(key));
            if (!text.isEmpty()) {
                texts.put(key, text);
                if (MATERIAL_KEYS.contains(key)) materialTexts.put(key, text);
            }
        }
        if (texts.isEmpty()) throw new NoPlantInformation();
        CompletableFuture<JsonNode> glossary = CompletableFuture.supplyAsync(
                () -> optional("glossary", skill("glossary")
                        + "\nDevuelve por categoría listas de objetos word y meaning.", texts.toString()), enrichment);
        CompletableFuture<JsonNode> materials = materialTexts.isEmpty()
                ? CompletableFuture.completedFuture(mapper.createObjectNode())
                : CompletableFuture.supplyAsync(() -> optional("materials", skill("materials")
                        + "\nDevuelve por categoría listas de objetos name y detail.",
                        plant.path("scientific_name").asText() + "\n" + materialTexts), enrichment);
        JsonNode g = glossary.join();
        JsonNode m = materials.join();
        ArrayNode sections = plant.putArray("sections");
        for (int i = 0; i < KEYS.size(); i++) {
            String key = KEYS.get(i);
            ObjectNode section = sections.addObject().put("key", key).put("title", TITLES.get(i))
                    .put("body", texts.path(key).asText(FALLBACK) + CONDITIONS);
            section.set("terms", sanitized(g.path(key), "word", "meaning", 80, 300, 12));
            section.set("materials", sanitized(m.path(key), "name", "detail", 120, 400, 10));
        }
        return plant;
    }

    public String skill(String id) {
        try {
            return new ClassPathResource("skills/" + id + ".md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Falta una instrucción de IA en el paquete.", error);
        }
    }

    private JsonNode ask(String step, String system, String user) {
        return ai.json(step, List.of(AiClient.message("system", system), AiClient.message("user", user)), false);
    }

    private JsonNode optional(String step, String system, String user) {
        try {
            return ask(step, system, user);
        } catch (AiClient.Failure error) {
            return mapper.createObjectNode();
        }
    }

    private JsonNode array(JsonNode input) {
        return input.isArray() ? input : mapper.createArrayNode();
    }

    private ArrayNode sanitized(JsonNode entries, String a, String b, int la, int lb, int count) {
        ArrayNode result = mapper.createArrayNode();
        for (JsonNode item : array(entries)) {
            String left = clean(item.path(a));
            String right = clean(item.path(b));
            if (!left.isBlank() && !right.isBlank()) result.addObject().put(a, limit(left, la)).put(b, limit(right, lb));
            if (result.size() == count) break;
        }
        return result;
    }

    private static String clean(JsonNode node) {
        String text = node.isTextual() ? node.asText().strip() : "";
        String normalized = text.toLowerCase(Locale.ROOT).replaceFirst("\\.+$", "");
        return UNKNOWN.contains(normalized) ? "" : text;
    }

    private static String limit(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    public static final class NoPlantInformation extends RuntimeException {}
}
