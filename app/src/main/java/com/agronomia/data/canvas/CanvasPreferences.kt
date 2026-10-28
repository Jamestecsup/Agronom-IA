package com.agronomia.data.canvas

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CanvasAuthState(
    val domain: String = DEFAULT_CANVAS_DOMAIN,
    val token: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val isConnected: Boolean = false
) {
    companion object {
        const val DEFAULT_CANVAS_DOMAIN = "https://tecsup.instructure.com"
    }
}

object CanvasPreferences {
    private const val PREFS_NAME = "agronomia_canvas_prefs"
    private const val KEY_DOMAIN = "canvas_domain"
    private const val KEY_TOKEN = "canvas_token"
    private const val KEY_USER_NAME = "canvas_user_name"
    private const val KEY_USER_EMAIL = "canvas_user_email"

    private val _authState = MutableStateFlow(CanvasAuthState())
    val authState: StateFlow<CanvasAuthState> = _authState.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val prefs = getPrefs(context)
        val domain = prefs.getString(KEY_DOMAIN, CanvasAuthState.DEFAULT_CANVAS_DOMAIN) ?: CanvasAuthState.DEFAULT_CANVAS_DOMAIN
        val token = prefs.getString(KEY_TOKEN, "").orEmpty()
        val userName = prefs.getString(KEY_USER_NAME, "").orEmpty()
        val userEmail = prefs.getString(KEY_USER_EMAIL, "").orEmpty()

        _authState.value = CanvasAuthState(
            domain = domain,
            token = token,
            userName = userName,
            userEmail = userEmail,
            isConnected = token.isNotBlank()
        )
        initialized = true
    }

    fun saveSession(context: Context, domain: String, token: String, userName: String, userEmail: String) {
        val cleanDomain = domain.trim().trimEnd('/')
        val cleanToken = token.trim()
        getPrefs(context).edit()
            .putString(KEY_DOMAIN, cleanDomain)
            .putString(KEY_TOKEN, cleanToken)
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_USER_EMAIL, userEmail)
            .apply()

        _authState.value = CanvasAuthState(
            domain = cleanDomain,
            token = cleanToken,
            userName = userName,
            userEmail = userEmail,
            isConnected = cleanToken.isNotBlank()
        )
    }

    fun clearSession(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .apply()

        _authState.value = _authState.value.copy(
            token = "",
            userName = "",
            userEmail = "",
            isConnected = false
        )
    }

    fun getDomain(context: Context): String {
        return getPrefs(context).getString(KEY_DOMAIN, CanvasAuthState.DEFAULT_CANVAS_DOMAIN)
            ?: CanvasAuthState.DEFAULT_CANVAS_DOMAIN
    }

    fun getToken(context: Context): String {
        return getPrefs(context).getString(KEY_TOKEN, "").orEmpty()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
