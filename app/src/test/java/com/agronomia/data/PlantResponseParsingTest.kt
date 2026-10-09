package com.agronomia.data

import com.agronomia.data.remote.PlantResultDto
import com.agronomia.data.repository.PlantRepositoryImpl
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del parseo tolerante de la respuesta de la IA.
 * No requieren Android: solo serialización y la extracción de JSON.
 */
class PlantResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }

    private val repository = PlantRepositoryImpl(json = json)

    @Test
    fun `extrae JSON envuelto en bloque markdown`() {
        val input = "```json\n{\"commonName\":\"Cactus\",\"scientificName\":\"Opuntia\",\"confidence\":0.9}\n```"
        val extracted = repository.extractJsonObject(input)

        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
        val dto = json.decodeFromString<PlantResultDto>(extracted)
        assertEquals("Cactus", dto.commonName)
        assertEquals("Opuntia", dto.scientificName)
    }

    @Test
    fun `extrae JSON con texto adicional alrededor`() {
        val input = "Claro, aquí está: " +
            "{\"commonName\":\"Rosa\",\"scientificName\":\"Rosa canina\",\"confidence\":0.87} " +
            "Espero que ayude."
        val extracted = repository.extractJsonObject(input)
        val dto = json.decodeFromString<PlantResultDto>(extracted)

        assertEquals("Rosa", dto.commonName)
        assertEquals("Rosa canina", dto.scientificName)
        assertEquals(0.87, dto.confidence, 0.0001)
    }

    @Test
    fun `campos ausentes usan valores por defecto`() {
        val dto = json.decodeFromString<PlantResultDto>("{}")

        assertTrue(dto.commonName.isEmpty())
        assertTrue(dto.scientificName.isEmpty())
        assertEquals(0.0, dto.confidence, 0.0001)
    }

    @Test
    fun `ignora campos desconocidos`() {
        val dto = json.decodeFromString<PlantResultDto>(
            "{\"commonName\":\"Rosa\",\"campoExtra\":123}"
        )

        assertEquals("Rosa", dto.commonName)
    }
}
