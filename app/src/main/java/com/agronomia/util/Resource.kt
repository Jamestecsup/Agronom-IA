package com.agronomia.util

// TODO: Clases utilitarias para manejo de estados de datos (éxito, error, carga)

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}
