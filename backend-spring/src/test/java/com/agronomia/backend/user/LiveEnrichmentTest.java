package com.agronomia.backend.user;

import com.agronomia.backend.ai.AiClient;
import com.agronomia.backend.ai.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "LIVE_AI", matches = "true")
class LiveEnrichmentTest {
    @Test
    void requestsOnlyGlossaryAndMaterials() throws Exception {
        var config = new Properties();
        Path dotenv = Path.of(".env");
        if (Files.isRegularFile(dotenv)) {
            try (var reader = Files.newBufferedReader(dotenv)) {
                config.load(reader);
            }
        }
        var json = new ObjectMapper();
        var ai = new OpenAiClient(json, required(config, "AI_BASE_URL"), required(config, "AI_API_KEY"),
                required(config, "AI_MODEL"), "", setting(config, "AI_AUX_MODEL"), 2, false);
        var cards = new PlantCards(json, ai, false, "");
        try {
            var example = json.createObjectNode()
                    .put("description", "Herbácea anual de tallo erguido y hojas alternas.")
                    .put("light", "Crece a pleno sol. Ajusta la exposición durante la aclimatación.")
                    .put("watering", "Riega en la base. Comprueba la humedad del sustrato y evita encharcamientos.")
                    .put("soil", "Prefiere suelos francos con buen drenaje y materia orgánica.")
                    .put("climate", "Protege las plántulas de las heladas y los vientos fuertes.")
                    .put("flowering", "Las flores se agrupan en un capítulo y atraen polinizadores.")
                    .put("uses", "Su uso ornamental aporta estructura y color al jardín.")
                    .put("care", "Instala un tutor en lugares expuestos y revisa el follaje con regularidad.");
            var result = cards.enrichments("Helianthus annuus", example,
                    AiClient.Deadline.after(Duration.ofSeconds(150)), false);
            assertThat(count(result.glossary())).as("términos válidos de glosario").isPositive();
            assertThat(count(result.materials())).as("materiales válidos").isPositive();
        } finally {
            cards.stop();
        }
    }

    private static int count(JsonNode response) {
        return StreamSupport.stream(response.spliterator(), false).mapToInt(JsonNode::size).sum();
    }

    private static String required(Properties config, String key) {
        String value = setting(config, key);
        if (value.isBlank()) throw new IllegalStateException("Falta " + key + " para la prueba en vivo.");
        return value;
    }

    private static String setting(Properties config, String key) {
        return System.getenv().getOrDefault(key, config.getProperty(key, ""));
    }
}
