package com.correa.agronom_ia

import android.content.Context

internal object BotanyInstructionsRepository {
    private const val ASSET_PATH = "botany-assistant/SKILL.md"

    fun load(context: Context): String {
        val markdown = context.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val body = markdown.substringAfter("---", "").substringAfter("---", "").trim()
        require(body.isNotBlank()) { "Las instrucciones de DeepSeek están vacías." }
        return body
    }
}
