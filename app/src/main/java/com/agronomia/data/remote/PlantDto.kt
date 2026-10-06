package com.agronomia.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// TODO: Definir Data Transfer Objects (DTOs) que representen la respuesta JSON de la API

@Serializable
data class PlantResponseDto(
    // TODO: Mapear campos de la respuesta JSON (nombre, sugerencias, probabilidad, etc.)
    @SerialName("id")
    val id: String? = null,
    @SerialName("name")
    val name: String? = null
)
