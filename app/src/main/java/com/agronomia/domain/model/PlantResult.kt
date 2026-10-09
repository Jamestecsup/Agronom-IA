package com.agronomia.domain.model

/**
 * Resultado final mostrado al usuario: la especie identificada (por Pl@ntNet o,
 * como respaldo, por Gemini visión) más la información generada por Gemini.
 */
data class PlantResult(
    val commonName: String,
    val scientificName: String,
    val family: String,
    val confidence: Float,
    val description: String,
    val uses: String,
    val care: String
)
