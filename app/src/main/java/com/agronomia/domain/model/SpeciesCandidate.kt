package com.agronomia.domain.model

/**
 * Especie candidata de Pl@ntNet para desambiguación: cuando la identificación
 * no alcanza certeza suficiente, el agricultor elige entre las más parecidas.
 */
data class SpeciesCandidate(
    val scientificName: String,
    val authorship: String = "",
    val commonNames: List<String> = emptyList(),
    val genus: String = "",
    val family: String = "",
    val confidence: Float = 0f,
    val gbifId: String = "",
    val powoId: String = "",
    val iucnCategory: String = ""
)
