package com.sonix.player.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sonix.player.data.Track
import com.sonix.player.player.PlaybackState
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class ExploreScreenUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testExploreScreenDisplaysTracks() {
        val sampleTrack = Track("1", "Ambient Waves", "Helix", "SoundHelix", "url", "6:12")
        
        composeTestRule.setContent {
            ExploreScreen(
                tracks = listOf(sampleTrack),
                onlineSearchResults = emptyList(),
                isSearchingOnline = false,
                onlineSearchError = null,
                isSyncing = false,
                playbackState = PlaybackState(),
                onSearchOnline = { _, _ -> },
                onClearOnlineSearch = {},
                onTrackClick = {},
                onDownloadClick = {},
                onDeleteClick = {},
                onPlayArtistClick = {},
                onPlayAlbumClick = {},
                onAddToPlaylistClick = {}
            )
        }

        // Verify tracks header is shown
        composeTestRule.onNodeWithText("Músicas Disponíveis").assertIsDisplayed()
        
        // Verify specific track title & artist is shown
        composeTestRule.onNodeWithText("Ambient Waves").assertIsDisplayed()
        composeTestRule.onNodeWithText("Helix").assertIsDisplayed()
    }

    @Test
    fun testTrackClickTriggersCallback() {
        val sampleTrack = Track("1", "Ambient Waves", "Helix", "SoundHelix", "url", "6:12")
        var clickedTrack: Track? = null

        composeTestRule.setContent {
            ExploreScreen(
                tracks = listOf(sampleTrack),
                onlineSearchResults = emptyList(),
                isSearchingOnline = false,
                onlineSearchError = null,
                isSyncing = false,
                playbackState = PlaybackState(),
                onSearchOnline = { _, _ -> },
                onClearOnlineSearch = {},
                onTrackClick = { clickedTrack = it },
                onDownloadClick = {},
                onDeleteClick = {},
                onPlayArtistClick = {},
                onPlayAlbumClick = {},
                onAddToPlaylistClick = {}
            )
        }

        // Perform click on the track row
        composeTestRule.onNodeWithText("Ambient Waves").performClick()

        // Assert callback was triggered with the correct track
        assertEquals(sampleTrack, clickedTrack)
    }
}
