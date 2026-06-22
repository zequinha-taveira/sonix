package com.sonix.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testNavigationBetweenTabs() {
        // Initially should show ExploreScreen
        composeTestRule.onNodeWithText("Músicas Disponíveis").assertIsDisplayed()

        // Navigate to Downloads tab
        composeTestRule.onNodeWithText("Downloads").performClick()
        composeTestRule.onNodeWithText("Músicas Baixadas").assertIsDisplayed()

        // Navigate to Playlists tab
        composeTestRule.onNodeWithText("Playlists").performClick()
        composeTestRule.onNodeWithText("Minhas Playlists").assertIsDisplayed()

        // Go back to Explorar
        composeTestRule.onNodeWithText("Explorar").performClick()
        composeTestRule.onNodeWithText("Músicas Disponíveis").assertIsDisplayed()
    }
}
