package com.agronomia.domain.model

/**
 * Resultado final mostrado al usuario: la especie identificada (por Pl@ntNet o,
 * como respaldo, por la IA de visión) más la información generada por la IA,
 * una categoría de cuidado por sección.
 */
data class PlantResult(
    val commonName: String,
    val scientificName: String,
    val family: String,
    val confidence: Float,
    val description: String,
    val light: String,
    val watering: String,
    val soil: String,
    val climate: String,
    val flowering: String,
    val uses: String,
    val care: String
)