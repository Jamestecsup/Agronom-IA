package com.agronomia.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs para la API de Pl@ntNet (identificación de especie por imagen):
 *
 *   POST {PLANTNET_BASE_URL}v2/identify/{project}?api-key=...&lang=...&nb-results=...
 *   Cuerpo multipart: "images" (JPG/PNG) + "organs"
 *
 * La respuesta trae "results" ordenados por "score" (0..1) y "bestMatch".
 */

@Serializable
data class PlantNetResponse(
    /** Nombre científico completo de la mejor coincidencia (atajo que da Pl@ntNet). */
    val bestMatch: String? = null,
    /** Candidatos, de mayor a menor puntuación. */
    val results: List<PlantNetResult> = emptyList(),
    /** Cuota diaria restante. */
    @SerialName("remainingIdentificationRequests")
    val remainingIdentificationRequests: Int? = null
)

@Serializable
data class PlantNetResult(
    /** Confianza de esta especie, entre 0 y 1. */
    val score: Double = 0.0,
    val species: PlantNetSpecies? = null
)

@Serializable
data class PlantNetSpecies(
    @SerialName("scientificNameWithoutAuthor")
    val scientificNameWithoutAuthor: String = "",
    @SerialName("scientificName")
    val scientificName: String = "",
    @SerialName("commonNames")
    val commonNames: List<String> = emptyList(),
    val family: PlantNetTaxon? = null
)

@Serializable
data class PlantNetTaxon(
    @SerialName("scientificNameWithoutAuthor")
    val scientificNameWithoutAuthor: String = ""
)
