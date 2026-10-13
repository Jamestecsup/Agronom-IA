package com.correa.agronom_ia

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ConversationScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun welcomeComposerAndConversationActionsAreAvailable() {
        composeRule.onNodeWithText("Más").performClick()
        composeRule.onNodeWithText("Nueva conversación").performClick()
        composeRule.onNodeWithText("¿Qué planta te gustaría cuidar?").assertIsDisplayed()
        composeRule.onNodeWithText("Fotos").assertIsDisplayed()
        composeRule.onNodeWithText("Cámara").assertIsDisplayed()
        composeRule.onNodeWithText("Enviar").assertIsDisplayed()

        composeRule.onNodeWithText("Más").performClick()
        composeRule.onNodeWithText("Nueva conversación").assertIsDisplayed()
        composeRule.onNodeWithText("Nueva conversación").performClick()
        composeRule.onNodeWithText("¿Qué planta te gustaría cuidar?").assertIsDisplayed()
    }
}
