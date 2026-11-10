package com.agronomia.domain.model

/**
 * Resultado final mostrado al usuario: la especie identificada (por Pl@ntNet o,
 * como respaldo, por la IA de visión) más las 8 secciones de cuidado, cada una
 * con su texto extenso y su glosario de palabras difíciles.
 */
data class PlantResult(
    val commonName: String,
    val scientificName: String,
    val family: String,
    val confidence: Float,
    val sections: List<PlantSection> = emptyList()
)