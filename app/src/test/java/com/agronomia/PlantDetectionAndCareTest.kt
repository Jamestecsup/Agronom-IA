package com.agronomia

import com.agronomia.data.remote.ChatCompletionRequest
import com.agronomia.data.remote.ChatCompletionResponse
import com.agronomia.data.remote.ChatResponseMessage
import com.agronomia.data.remote.Choice
import com.agronomia.data.remote.PlantApiService
import com.agronomia.data.remote.PlantNetApiService
import com.agronomia.data.remote.PlantNetResponse
import com.agronomia.data.remote.PlantNetResult
import com.agronomia.data.remote.PlantNetSpecies
import com.agronomia.data.repository.AnalysisResult
import com.agronomia.data.repository.PlantRepositoryImpl
import com.agronomia.domain.model.SpeciesCandidate
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Servicio Fake de Pl@ntNet que simula la respuesta de reconocimiento de plantas.
 */
class FakePlantNetApiService(
    private val mockScore: Double = 0.85,
    private val mockScientificName: String = "Helianthus annuus",
    private val mockCommonName: String = "Girasol"
) : PlantNetApiService {
    override suspend fun identify(
        url: String,
        images: List<MultipartBody.Part>,
        organs: List<MultipartBody.Part>
    ): PlantNetResponse {
        return PlantNetResponse(
            bestMatch = mockScientificName,
            results = listOf(
                PlantNetResult(
                    score = mockScore,
                    species = PlantNetSpecies(
                        scientificNameWithoutAuthor = mockScientificName,
                        scientificNameAuthorship = "L.",
                        scientificName = "$mockScientificName L.",
                        commonNames = listOf(mockCommonName, "Girasol común")
                    )
                )
            )
        )
    }
}

/**
 * Servicio Fake de la API de IA (Gemini / OpenAI) que simula la generación
 * de textos de cuidados, glosario y materiales por categoría.
 */
class FakePlantApiService : PlantApiService {
    override suspend fun identifyPlant(
        url: String,
        authorization: String,
        request: ChatCompletionRequest
    ): ChatCompletionResponse {
        val userPrompt = request.messages.firstOrNull()?.content?.firstOrNull()?.text.orEmpty()

        val responseJson = when {
            userPrompt.contains("requisitos de luz", ignoreCase = true) -> {
                """{"text": "El girasol requiere exposición a pleno sol directo de 6 a 8 horas diarias para un crecimiento óptimo."}"""
            }
            userPrompt.contains("el riego de la planta", ignoreCase = true) -> {
                """{"text": "Regar de manera regular 2 a 3 veces por semana sin encharcar la tierra."}"""
            }
            userPrompt.contains("el suelo ideal", ignoreCase = true) -> {
                """{"text": "Prefiere suelos bien drenados, ricos en materia orgánica y con pH entre 6.0 y 7.5."}"""
            }
            userPrompt.contains("el clima ideal", ignoreCase = true) -> {
                """{"text": "Clima templado a cálido entre 18°C y 30°C. Sensible a heladas intensas."}"""
            }
            userPrompt.contains("cuidados de la planta", ignoreCase = true) -> {
                """{"text": "Aplicar fertilizante rico en fósforo y potasio antes de la floración y tutorar el tallo si es alto."}"""
            }
            userPrompt.contains("floración de la planta", ignoreCase = true) -> {
                """{"text": "Florece durante el verano con inflorescencias amarillas brillantes que siguen la trayectoria solar."}"""
            }
            userPrompt.contains("usos de la planta", ignoreCase = true) -> {
                """{"text": "Uso ornamental en jardines, extracción de aceite comestible y semillas para consumo."}"""
            }
            userPrompt.contains("describe EN EXTENSO", ignoreCase = true) -> {
                """{"text": "Planta herbácea anual de la familia Asteraceae caracterizada por su gran capítulo floral amarillo."}"""
            }
            userPrompt.contains("glosario", ignoreCase = true) ||
            userPrompt.contains("términos", ignoreCase = true) -> {
                """{"terms": [{"word": "Heliófita", "meaning": "Planta que requiere luz solar directa para crecer."}]}"""
            }
            userPrompt.contains("materiales", ignoreCase = true) ||
            userPrompt.contains("productos", ignoreCase = true) -> {
                """{"items": [{"name": "Substrato Universal", "detail": "Mezcla de turba y perlita para buen drenaje"}]}"""
            }
            userPrompt.contains("investigación", ignoreCase = true) ||
            userPrompt.contains("REFINE_PROMPT", ignoreCase = true) -> {
                """{"prompt": "Girasol (Helianthus annuus) es una planta heliófita que requiere sol directo y riego regular."}"""
            }
            else -> {
                """{"text": "Información agronómica sobre los cuidados de la planta con fertilizante y tierra fertilizada."}"""
            }
        }

        return ChatCompletionResponse(
            choices = listOf(
                Choice(
                    message = ChatResponseMessage(
                        role = "assistant",
                        content = responseJson
                    )
                )
            )
        )
    }
}

/**
 * Pruebas unitarias de detección de plantas y generación de categorías de cuidado.
 */
class PlantDetectionAndCareTest {

    @Test
    fun testPlantDetectionWithFullCareCategories() = runBlocking {
        val fakePlantNet = FakePlantNetApiService(
            mockScore = 0.85, // 85% de similitud (> 50% para validación directa)
            mockScientificName = "Helianthus annuus",
            mockCommonName = "Girasol"
        )
        val fakeAiService = FakePlantApiService()

        val repository = PlantRepositoryImpl(
            aiService = fakeAiService,
            plantNetService = fakePlantNet
        )

        // Simula la captura de una foto de planta
        val fakeImageBytes = byteArrayOf(1, 2, 3, 4, 5)
        val organs = listOf("flower")

        // Ejecuta el análisis de la planta
        val result = repository.analyze(listOf(fakeImageBytes), organs)

        // 1. Verifica que el resultado sea Identified (confianza >= 50%)
        assertTrue("El análisis debe ser Identified directamente", result is AnalysisResult.Identified)
        val identified = result as AnalysisResult.Identified
        val plantResult = identified.plant

        // 2. Verifica los datos de identificación de la planta
        assertEquals("Girasol", plantResult.commonName)
        assertEquals("Helianthus annuus", plantResult.scientificName)
        assertTrue("La confianza debe ser mayor a 80%", plantResult.confidence >= 0.80)

        // 3. Verifica que la planta contenga todas las secciones de cuidados necesarias
        val sections = plantResult.sections
        assertNotNull("Las secciones de cuidado no deben ser nulas", sections)
        assertTrue("Debe contener secciones de cuidado", sections.isNotEmpty())

        // Verifica la presencia de las categorías principales de cuidado
        val sectionKeys = sections.map { it.key }
        assertTrue("Debe incluir la categoría 'light' (Luz)", sectionKeys.contains("light"))
        assertTrue("Debe incluir la categoría 'watering' (Riego)", sectionKeys.contains("watering"))
        assertTrue("Debe incluir la categoría 'soil' (Suelo)", sectionKeys.contains("soil"))
        assertTrue("Debe incluir la categoría 'climate' (Clima)", sectionKeys.contains("climate"))
        assertTrue("Debe incluir la categoría 'care' (Cuidados)", sectionKeys.contains("care"))

        // 4. Verifica que cada sección contenga el texto descriptivo del cuidado
        val lightSection = sections.find { it.key == "light" }
        assertNotNull("La sección de Luz debe existir", lightSection)
        assertTrue(
            "El texto de Luz debe contener las horas de sol necesarias",
            lightSection!!.body.contains("sol directo")
        )

        val wateringSection = sections.find { it.key == "watering" }
        assertNotNull("La sección de Riego debe existir", wateringSection)
        assertTrue(
            "El texto de Riego debe contener la frecuencia recomendada",
            wateringSection!!.body.contains("2 a 3 veces por semana")
        )
    }

    @Test
    fun testConfirmingCandidateGeneratesCareSections() = runBlocking {
        val fakePlantNet = FakePlantNetApiService()
        val fakeAiService = FakePlantApiService()

        val repository = PlantRepositoryImpl(
            aiService = fakeAiService,
            plantNetService = fakePlantNet
        )

        val candidate = SpeciesCandidate(
            scientificName = "Helianthus annuus",
            commonNames = listOf("Girasol"),
            family = "Asteraceae",
            confidence = 0.35f // 35% de similitud (modo dudas)
        )

        // El usuario elige "Girasol" entre las opciones
        val result = repository.confirmCandidate(candidate, emptyList())

        assertTrue("La confirmación de la candidata debe ser exitosa", result.isSuccess)
        val plantResult = result.getOrThrow()

        assertEquals("Girasol", plantResult.commonName)
        assertEquals("Helianthus annuus", plantResult.scientificName)

        // Verifica que la confirmación genere la ficha de cuidados completa
        val sections = plantResult.sections
        assertTrue("Debe generar las secciones de cuidado al confirmar", sections.isNotEmpty())

        val careSection = sections.find { it.key == "care" }
        assertNotNull("La sección de Cuidados debe existir", careSection)
        assertTrue(
            "La sección de Cuidados debe incluir recomendaciones de fertilizante",
            careSection!!.body.contains("fertilizante")
        )
    }
}
