package com.agronomia.domain.model

// TODO: Definir el modelo de dominio para las plantas identificadas

data class Plant(
    val id: String = "",
    val commonName: String = "",
    val scientificName: String = "",
    val description: String = "",
    val confidence: Float = 0.0f,
    val imageUrl: String? = null
    // TODO: Agregar información sobre cuidados (riego, luz solar, enfermedades detectadas, etc.)
)
