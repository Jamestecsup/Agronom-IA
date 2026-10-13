package com.correa.agronom_ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun spaceBunnyParsesStructuredGuide() {
        val result = SpaceBunnyResponseParser.parse(
            """{"parts":[{"type":"text","text":"{\"watering\":\"Riega cuando se seque la superficie.\",\"lighting\":\"Luz indirecta.\",\"pet_toxicity\":\"No confirmada.\",\"warnings\":\"La especie requiere verificación.\"}"}]}"""
        )

        assertEquals("Riega cuando se seque la superficie.", result.watering)
        assertEquals("Luz indirecta.", result.lighting)
        assertEquals("No confirmada.", result.petToxicity)
        assertEquals("La especie requiere verificación.", result.warnings)
    }

    @Test(expected = IllegalStateException::class)
    fun spaceBunnyRejectsIncompleteGuide() {
        SpaceBunnyResponseParser.parse(
            """{"parts":[{"type":"text","text":"{\"watering\":\"Riega.\"}"}]}"""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun spaceBunnyRejectsMixedScriptGarbage() {
        SpaceBunnyResponseParser.parse(
            """{"parts":[{"type":"text","text":"{\"watering\":\"Riega cuando se seque.\",\"lighting\":\"Luz brillante.\",\"pet_toxicity\":\"No verificada.\",\"warnings\":\"Confirma la especie. 因为这是测试\"}"}]}"""
        )
    }
}
