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
    val iucnCategory: String = "",
    /**
     * Foto de referencia de internet (Wikimedia Commons) para reconocerla.
     * Vacía si no se encontró; nunca bloquea.
     */
    val imageUrl: String = "",
    /**
     * Rasgos únicos para reconocerla frente a las otras candidatas
     * (color/forma de flor y hoja, tamaño). Vacío si no se generó.
     */
    val hint: String = "",
    /**
     * Palabras difíciles del hint con su significado en contexto
     * (igual que el glosario de las categorías).
     */
    val hintTerms: List<WordMeaning> = emptyList()
)
