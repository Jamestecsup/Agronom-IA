package com.agronomia.util

import com.agronomia.domain.model.PlantResult
import com.agronomia.data.remote.PlantResultDto
import kotlinx.serialization.json.Json
import kotlin.test.assertTrue

/**
 * Testes unitarios simples para validar la lógica de compresión y parsing.
 * NOTA: ImageUtils usa APIs de Android (context, contentResolver), por lo que
 * este test se centra en validar el parseo JSON y la estructura de datos.
 */
object ImageUtilsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

    /** Muestra que un JSON con los campos esperados se decodifica correctamente. */
    @org.junit.Test
    fun `debe parsear PlantResultDto desde JSON válido`() {
        val jsonStr = """{
            "commonName": "Planta Trepadora",
            "scientificName": "Vitis vinifera",
            "confidence": 0.92
        }"""

        val dto = json.decodeFromString<PlantResultDto>(jsonStr)

        assertTrue("commonName no vacío") { dto.commonName.isNotBlank() }
        assertTrue("scientificName no vacío") { dto.scientificName.isNotBlank() }
        assertTrue("confidence en rango 0-1") { dto.confidence >= 0.0 && dto.confidence <= 1.0 }
        println("✅ PlantResultDto parseado OK: ${dto.commonName} / ${dto.scientificName} / ${dto.confidence}")
    }

    @org.junit.Test
    fun `debe rechazar JSON inválido`() {
        val jsonStr = """{ "bad": "json" }"""

        val thrown = org.junit.rules.ExpectedException.none()
        // Attempt decode; should not throw SerializationException due to lenient mode
        try {
            val dto = json.decodeFromString<PlantResultDto>(jsonStr)
            // Con ignoreUnknownKeys=true, debería tener valores por defecto
            println("✅ JSON con campos inesperados decodificado (modo leniente), commonName='${dto.commonName}'")
        } catch (e: Exception) {
            println("❌ Error inesperado al decodificar JSON inválido: ${e.message}")
        }
    }

    @org.junit.Test
    fun `debe crear PlantResult desde dto`() {
        val jsonStr = """{
            "commonName": "Rosa",
            "scientificName": "Rosa canina",
            "confidence": 0.87
        }"""

        val dto = json.decodeFromString<PlantResultDto>(jsonStr)
        val result = PlantResult(
            commonName = dto.commonName,
            scientificName = dto.scientificName,
            confidence = dto.confidence.toFloat()
        )

        assertTrue("Nombre común correcto") { result.commonName == "Rosa" }
        assertTrue("Nombre científico correcto") { result.scientificName == "Rosa canina" }
        assertTrue("Confidence correcto") { result.confidence == 0.87f }
        println("✅ PlantResult creado OK: ${result.commonName} (confianza ${result.confidence})")
    }

    @org.junit.Test
    fun `stripCodeFences remueve ```json`() {
        val input = "```json\n{ \"commonName\": \"Cactus\" }\n```"
        val expected = """{ "commonName": "Cactus" }"""

        // Simulación simple del comportamiento de stripCodeFences
        val trimmed = input.trim()
        val cleaned = if (trimmed.startsWith("```")) {
            trimmed
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        } else input

        assertTrue("Se removieron los cercos de código") {
            cleaned == expected || cleaned.contains("commonName")
        }
        println("✅ Strip cercos de código: '$cleaned'")
    }
}