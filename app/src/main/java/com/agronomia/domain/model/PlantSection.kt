package com.agronomia.domain.model

/**
 * Significado contextual de una palabra difícil: se muestra cuando el usuario
 * toca la palabra resaltada dentro del texto de una categoría.
 */
data class WordMeaning(
    val word: String,
    val meaning: String
)

/**
 * Bloque de una categoría de cuidado: título, texto extenso y glosario de
 * palabras difíciles con su significado en el contexto de la categoría.
 */
data class PlantSection(
    val key: String,
    val title: String,
    val body: String,
    val terms: List<WordMeaning> = emptyList()
)
