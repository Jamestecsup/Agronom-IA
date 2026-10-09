package com.agronomia.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Url

/**
 * Servicio Retrofit para la API de Pl@ntNet.
 *
 * La URL completa (con `api-key`, `project`, `lang` y `nb-results` como query)
 * se recibe en [identify] mediante [Url]. El cuerpo es multipart:
 *  - part `images`: la imagen JPEG.
 *  - part `organs`: `"auto"` para que Pl@ntNet detecte el órgano (hoja/flor/...).
 *
 * NOTA de seguridad: la api-key viaja en la URL, por eso este servicio usa un
 * cliente OkHttp SIN interceptor de logging (evita filtrar la clave en logcat).
 */
interface PlantNetApiService {

    @Multipart
    @POST
    suspend fun identify(
        @Url url: String,
        @Part images: MultipartBody.Part,
        @Part("organs") organs: RequestBody
    ): PlantNetResponse
}
