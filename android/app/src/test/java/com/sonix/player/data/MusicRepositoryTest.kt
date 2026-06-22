package com.sonix.player.data

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MusicRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val sharedPrefsDownloaded = mockk<SharedPreferences>(relaxed = true)
    private val sharedPrefsPlaylists = mockk<SharedPreferences>(relaxed = true)
    private val editorDownloaded = mockk<SharedPreferences.Editor>(relaxed = true)
    private val editorPlaylists = mockk<SharedPreferences.Editor>(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Mock shared preferences retrieval
        every { context.getSharedPreferences("downloaded_tracks_prefs", Context.MODE_PRIVATE) } returns sharedPrefsDownloaded
        every { context.getSharedPreferences("playlists_prefs", Context.MODE_PRIVATE) } returns sharedPrefsPlaylists

        every { sharedPrefsDownloaded.edit() } returns editorDownloaded
        every { sharedPrefsPlaylists.edit() } returns editorPlaylists

        every { editorDownloaded.putString(any(), any()) } returns editorDownloaded
        every { editorPlaylists.putString(any(), any()) } returns editorPlaylists

        // Mock internal filesDir for download path check
        every { context.filesDir } returns File(".")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPlaylists should parse correct JSON format and update state`() {
        val playlistsJson = """
            [
                {
                    "id": "p1",
                    "name": "Gym Mix",
                    "trackIds": ["1", "2"]
                }
            ]
        """.trimIndent()

        every { sharedPrefsPlaylists.getString("playlists_data", null) } returns playlistsJson

        val repository = MusicRepository(context)
        repository.loadPlaylists()

        val playlists = repository.playlists.value
        assertEquals(1, playlists.size)
        assertEquals("p1", playlists[0].id)
        assertEquals("Gym Mix", playlists[0].name)
        assertEquals(listOf("1", "2"), playlists[0].trackIds)
    }

    @Test
    fun `savePlaylist should append and save to sharedPreferences`() {
        val repository = MusicRepository(context)
        val playlist = Playlist("p_new", "Metal Core", listOf("4"))

        repository.savePlaylist(playlist)

        val playlists = repository.playlists.value
        assertTrue(playlists.any { it.id == "p_new" && it.name == "Metal Core" })

        // Verify it persists JSON correctly
        verify { editorPlaylists.putString("playlists_data", any()) }
        verify { editorPlaylists.apply() }
    }

    @Test
    fun `deletePlaylist should remove and save to sharedPreferences`() {
        val playlistsJson = """
            [
                {
                    "id": "p1",
                    "name": "Gym Mix",
                    "trackIds": ["1"]
                }
            ]
        """.trimIndent()
        every { sharedPrefsPlaylists.getString("playlists_data", null) } returns playlistsJson

        val repository = MusicRepository(context)
        repository.loadPlaylists()
        assertEquals(1, repository.playlists.value.size)

        repository.deletePlaylist("p1")
        assertEquals(0, repository.playlists.value.size)

        verify { editorPlaylists.putString("playlists_data", "[]") }
    }

    @Test
    fun `addTrackToPlaylist and removeTrackFromPlaylist should modify track lists`() {
        val repository = MusicRepository(context)
        val playlist = Playlist("p1", "Workout", listOf("1"))
        repository.savePlaylist(playlist)

        repository.addTrackToPlaylist("p1", "2")
        val updated = repository.playlists.value.first { it.id == "p1" }
        assertEquals(listOf("1", "2"), updated.trackIds)

        repository.removeTrackFromPlaylist("p1", "1")
        val finalPlaylist = repository.playlists.value.first { it.id == "p1" }
        assertEquals(listOf("2"), finalPlaylist.trackIds)
    }

    @Test
    fun `savePlaylist should sanitize illegal XML control characters`() {
        val repository = MusicRepository(context)
        val playlist = Playlist("p_bad", "Gym\u0000Mix\u000B", listOf("1"))

        repository.savePlaylist(playlist)

        val slot = slot<String>()
        verify { editorPlaylists.putString("playlists_data", capture(slot)) }
        
        // Assert XML control character is stripped
        assertTrue(slot.captured.contains("GymMix"))
        assertFalse(slot.captured.contains("\u0000"))
        assertFalse(slot.captured.contains("\u000B"))
    }

    @Test
    fun `online search should trigger aggregator and update states`() = runTest {
        mockkConstructor(SearchAggregator::class)
        val mockTrack = Track("itunes_1", "Show Me How to Live", "Audioslave", "Audioslave", "url", "4:37")
        coEvery { anyConstructed<SearchAggregator>().search("Audioslave", any()) } returns listOf(mockTrack)

        val repository = MusicRepository(context)

        // Run search
        repository.searchOnline("Audioslave")

        testScheduler.advanceUntilIdle()

        assertEquals(listOf(mockTrack), repository.onlineSearchResults.value)
        assertEquals(false, repository.isSearchingOnline.value)
        assertNull(repository.onlineSearchError.value)

        unmockkConstructor(SearchAggregator::class)
    }
}
