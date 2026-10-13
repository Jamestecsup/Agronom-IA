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
    fun spaceBunnyExtractsMarkdownReply() {
        val result = SpaceBunnyResponseParser.parse(
            """{"parts":[{"type":"text","text":"## Riego\nRiega cuando la superficie del sustrato esté seca.\n\n## Toxicidad\nNo hay información fiable."}]}"""
        )

        assertTrue(result.startsWith("## Riego"))
        assertTrue(result.contains("No hay información fiable."))
    }

    @Test(expected = IllegalStateException::class)
    fun spaceBunnyRejectsIncompleteGuide() {
        SpaceBunnyResponseParser.parse(
            """{"parts":[{"type":"text","text":"  "}]}"""
        )
    }

    @Test
    fun spaceBunnyCanReplyInTheLanguageRequestedByTheUser() {
        val result = SpaceBunnyResponseParser.parse(
            """{"parts":[{"type":"text","text":"如果用户要求中文，就用中文回答。"}]}""",
            userQuestion = "请用中文回答"
        )
        assertTrue(result.contains("中文"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun spaceBunnyRejectsUnrequestedMixedWritingSystems() {
        SpaceBunnyResponseParser.parse(
            """{"parts":[{"type":"text","text":"Comprueba la humedad. 湿度"}]}""",
            userQuestion = "¿Cuándo riego?"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun spaceBunnyRejectsOversizedReply() {
        val longReply = "x".repeat(8_001)
        SpaceBunnyResponseParser.parse("""{"parts":[{"type":"text","text":"$longReply"}]}""")
    }

    @Test
    fun skillInstructionsAreBundledForTheRuntimePrompt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val instructions = SpaceBunnySkillRepository.load(context)

        assertTrue(instructions.contains("Pl@ntNet es el único responsable de la identificación visual"))
        assertTrue(instructions.contains("Una falta de datos sobre toxicidad no significa que la planta sea segura"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun spaceBunnyRejectsInternalPlaceholderText() {
        SpaceBunnyResponseParser.parse(
            """{"parts":[{"type":"text","text":"Usa agua con_filtro en cada riego."}]}"""
        )
    }
}
