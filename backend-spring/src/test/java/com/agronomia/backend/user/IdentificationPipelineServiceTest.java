package com.agronomia.backend.user;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentificationPipelineServiceTest {

    @Test
    void usesInjectedClientsAndReturnsIdentifiedPlant() {
        PlantIdentificationClient plantNet = mock(PlantIdentificationClient.class);
        PlantAiClient ai = mock(PlantAiClient.class);
        when(plantNet.identify(anyList(), anyString(), anyString(), anyString())).thenReturn(Map.of(
                "results", List.of(Map.of(
                        "score", 0.91,
                        "species", Map.of(
                                "scientificNameWithoutAuthor", "Helianthus annuus",
                                "scientificNameAuthorship", "L.",
                                "commonNames", List.of("Girasol"),
                                "genus", Map.of("scientificNameWithoutAuthor", "Helianthus"),
                                "family", Map.of("scientificNameWithoutAuthor", "Asteraceae")
                        )
                ))
        ));
        when(ai.loadSkill(anyString())).thenReturn("skill");
        when(ai.loadCategorySkill(anyString())).thenReturn("cat");
        when(ai.jsonCall(anyString(), anyString(), anyDouble())).thenAnswer(inv -> {
            String system = inv.getArgument(0, String.class);
            if (system.contains("VARIAS categor")) return Map.of(); // glossary/materials batch
            if (system.contains("ficha completa de la planta")) {
                return Map.of(
                        "description", "desc", "light", "light", "watering", "watering", "soil", "soil",
                        "climate", "climate", "flowering", "flowering", "uses", "uses", "care", "care"
                );
            }
            return Map.of("prompt", "p"); // refine-prompt or fallback
        });

        IdentificationPipelineService service = new IdentificationPipelineService(plantNet, ai, "all", "es");
        Map<String, Object> result = service.identify(List.of(new byte[]{1, 2, 3}), "auto");

        assertThat(result.get("status")).isEqualTo("identified");
        Map<?, ?> plant = (Map<?, ?>) result.get("plant");
        assertThat(plant.get("scientific_name")).isEqualTo("Helianthus annuus");
        verify(plantNet).identify(anyList(), eq("auto"), eq("all"), eq("es"));
        verify(ai, atLeastOnce()).jsonCall(anyString(), anyString(), anyDouble());
    }
}
