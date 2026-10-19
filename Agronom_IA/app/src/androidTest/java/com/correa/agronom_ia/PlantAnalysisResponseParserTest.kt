package com.correa.agronom_ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

class PlantAnalysisResponseParserTest {
    @Test
    fun requestAcceptsOneOrFiveImages() {
        PlantAnalysisRequestValidator.validatePhotoCount(1)
        PlantAnalysisRequestValidator.validatePhotoCount(5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun requestRejectsEmptySelection() {
        PlantAnalysisRequestValidator.validatePhotoCount(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun requestRejectsMoreThanFiveImages() {
        PlantAnalysisRequestValidator.validatePhotoCount(6)
    }

    @Test
    fun plantNetParsesScientificFamilyCommonNameAndScore() {
        val result = PlantNetResponseParser.parse(
            """{"results":[{"score":0.91,"species":{"scientificNameWithoutAuthor":"Rosa canina","family":{"scientificName":"Rosaceae"},"commonNames":["Escaramujo"]}},{"score":0.1,"species":{"scientificNameWithoutAuthor":"Rosa rubiginosa"}}]}"""
        )

        assertEquals("Rosa canina", result?.scientificName)
        assertEquals("Rosaceae", result?.family)
        assertEquals("Escaramujo", result?.commonName)
        assertEquals(0.91, result?.score ?: 0.0, 0.0001)
        assertFalse(result?.uncertain ?: true)
        assertEquals(listOf("Rosa rubiginosa"), result?.alternatives)
    }

    @Test
    fun plantNetMarksWeakOrCloselyRankedResultsAsUncertain() {
        val result = PlantNetResponseParser.parse(
            """{"results":[{"score":0.52,"species":{"scientificName":"Plant A"}},{"score":0.48,"species":{"scientificName":"Plant B"}}]}"""
        )

        assertTrue(result?.uncertain ?: false)
    }

    @Test
    fun plantNetReturnsNoMatchForEmptyResults() {
        assertNull(PlantNetResponseParser.parse("""{"results":[]}"""))
    }

    @Test
    fun modelCatalogKeepsBothConfiguredProvidersAndPublicMetadata() {
        val models = AiModelCatalogParser.parse(
            """{"models":[{"id":"deepseek-opencode","name":"DeepSeek V4.1 Flash Free","provider":"OpenCode","modelId":"deepseek-v4.1-flash-free","available":true,"welcomeMessage":"Hola DeepSeek"},{"id":"qwen-local","name":"Qwen 3.6 35B A3B FP8","provider":"Open WebUI","modelId":"Qwen/Qwen3.6-35B-A3B-FP8","available":false,"unavailableReason":"offline","welcomeMessage":"Hola Qwen"}]}"""
        )

        assertEquals(listOf("deepseek-opencode", "qwen-local"), models.map { it.id })
        assertEquals(true, models[0].available)
        assertEquals(false, models[1].available)
        assertEquals("Open WebUI", models[1].provider)
        assertEquals("Hola Qwen", models[1].welcomeMessage)
    }

    @Test
    fun modelCatalogIgnoresUnknownProviderIdsAndRetainsFallbackOptions() {
        val models = AiModelCatalogParser.parse("""{"models":[{"id":"arbitrary-provider","available":true}]}""")
        assertEquals(setOf("deepseek-opencode", "qwen-local"), models.map { it.id }.toSet())
        assertTrue(models.all { it.available == null })
    }

    @Test
    fun serverSentEventsParseDeltasCompletionAndProviderErrors() {
        assertEquals(AiSseEvent.Delta("Hola"), AiSseEventParser.parse("delta", """{"text":"Hola"}"""))
        assertEquals(AiSseEvent.Done, AiSseEventParser.parse("done", """{"providerId":"qwen-local"}"""))
        assertEquals(AiSseEvent.Error("Qwen no disponible"), AiSseEventParser.parse("error", """{"message":"Qwen no disponible"}"""))
        assertEquals(AiSseEvent.Ignored, AiSseEventParser.parse("heartbeat", "{}"))
    }

    @Test
    fun chatRequestCarriesOnlyTheSelectedLogicalProviderAndConversation() {
        val turns = listOf(ChatTurn("user", "Hola"), ChatTurn("assistant", "¿Cómo puedo ayudar?"))
        AiModelCatalogParser.defaults.forEach { model ->
            val payload = AiChatApi.requestPayload(model.id, turns)
            assertEquals(model.id, payload.getString("providerId"))
            assertEquals("Hola", payload.getJSONArray("messages").getJSONObject(0).getString("content"))
            assertEquals("assistant", payload.getJSONArray("messages").getJSONObject(1).getString("role"))
            assertFalse(payload.has("apiKey"))
            assertFalse(payload.has("baseUrl"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun chatRequestDoesNotAllowArbitraryProviderIds() {
        AiChatApi.requestPayload("http://untrusted-host", listOf(ChatTurn("user", "Hola")))
    }

    @Test(expected = IllegalStateException::class)
    fun serverSentEventsRejectMalformedDeltaPayload() {
        AiSseEventParser.parse("delta", "no-json")
    }

    @Test
    fun selectingModelPreservesExistingMessagesAndSurvivesRestore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("plant_chat_history", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().putString("messages", """[{"id":"old-assistant","role":"assistant","text":"Respuesta anterior","modelId":"deepseek-opencode"}]""").apply()
        try {
            val model = PlantPhotoViewModel()
            model.restoreConversation(context)
            model.selectModel(context, AiModelCatalogParser.QWEN_ID)

            assertEquals(AiModelCatalogParser.QWEN_ID, model.state.value.selectedModelId)
            assertEquals("Respuesta anterior", model.state.value.conversation.single().text)
            assertEquals(AiModelCatalogParser.DEEPSEEK_ID, model.state.value.conversation.single().modelId)

            val restored = PlantPhotoViewModel()
            restored.restoreConversation(context)
            assertEquals(AiModelCatalogParser.QWEN_ID, restored.state.value.selectedModelId)
            assertEquals(AiModelCatalogParser.DEEPSEEK_ID, restored.state.value.conversation.single().modelId)
        } finally {
            prefs.edit().clear().apply()
        }
    }
}
