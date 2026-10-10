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
 * Material o alternativa útil para una categoría de cuidado: nombre (genérico,
 * con marcas de ejemplo cuando aplique) y detalle práctico de uso.
 */
data class MaterialItem(
    val name: String,
    val detail: String
)

/**
 * Bloque de una categoría de cuidado: título, texto extenso, glosario de
 * palabras difíciles y (solo en categorías de cuidado) materiales/alternativas.
 */
data class PlantSection(
    val key: String,
    val title: String,
    val body: String,
    val terms: List<WordMeaning> = emptyList(),
    val materials: List<MaterialItem> = emptyList()
)
