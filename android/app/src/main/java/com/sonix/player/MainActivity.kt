package com.sonix.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sonix.player.data.MusicRepository
import com.sonix.player.player.AudioPlayerManager
import com.sonix.player.ui.components.PlayerBar
import com.sonix.player.ui.screens.DownloadsScreen
import com.sonix.player.ui.screens.ExploreScreen
import com.sonix.player.ui.theme.CyanSecondary
import com.sonix.player.ui.theme.DarkSurface
import com.sonix.player.ui.theme.SonixTheme
import com.sonix.player.ui.theme.VioletPrimary

class MainActivity : ComponentActivity() {

    private lateinit var repository: MusicRepository
    private lateinit var playerManager: AudioPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = MusicRepository(applicationContext)
        playerManager = AudioPlayerManager(applicationContext)

        setContent {
            SonixTheme {
                var currentTab by remember { mutableStateOf("explore") }
                val tracks by repository.tracks.collectAsState()
                val onlineSearchResults by repository.onlineSearchResults.collectAsState()
                val isSearchingOnline by repository.isSearchingOnline.collectAsState()
                val onlineSearchError by repository.onlineSearchError.collectAsState()
                val playbackState by playerManager.playbackState.collectAsState()

                // Keep playback manager playlist up to date when tracks list updates (e.g., download complete)
                LaunchedEffect(tracks, onlineSearchResults) {
                    playerManager.setPlaylist((tracks + onlineSearchResults).distinctBy { it.id })
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = DarkSurface.copy(alpha = 0.9f),
                            contentColor = Color.White
                        ) {
                            NavigationBarItem(
                                selected = currentTab == "explore",
                                onClick = { currentTab = "explore" },
                                icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                                label = { Text("Explorar") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = VioletPrimary,
                                    selectedTextColor = VioletPrimary,
                                    indicatorColor = Color.White.copy(alpha = 0.1f),
                                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                    unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                )
                            )
                            NavigationBarItem(
                                selected = currentTab == "downloads",
                                onClick = { currentTab = "downloads" },
                                icon = { Icon(Icons.Default.DownloadDone, contentDescription = "Downloads") },
                                label = { Text("Downloads") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyanSecondary,
                                    selectedTextColor = CyanSecondary,
                                    indicatorColor = Color.White.copy(alpha = 0.1f),
                                    unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                    unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Switch between Screens
                        when (currentTab) {
                            "explore" -> {
                                ExploreScreen(
                                    tracks = tracks,
                                    onlineSearchResults = onlineSearchResults,
                                    isSearchingOnline = isSearchingOnline,
                                    onlineSearchError = onlineSearchError,
                                    playbackState = playbackState,
                                    onSearchOnline = { query -> repository.searchOnline(query) },
                                    onClearOnlineSearch = { repository.clearOnlineSearch() },
                                    onTrackClick = { playerManager.play(it) },
                                    onDownloadClick = { repository.downloadTrack(it) },
                                    onDeleteClick = { repository.deleteTrack(it) },
                                    onPlayArtistClick = { artist ->
                                        val combined = (tracks + onlineSearchResults).distinctBy { it.id }
                                        val artistTracks = combined.filter { it.artist == artist }
                                        if (artistTracks.isNotEmpty()) {
                                            playerManager.setPlaylist(artistTracks)
                                            playerManager.play(artistTracks.first())
                                        }
                                    },
                                    onPlayAlbumClick = { album ->
                                        val combined = (tracks + onlineSearchResults).distinctBy { it.id }
                                        val albumTracks = combined.filter { it.album == album }
                                        if (albumTracks.isNotEmpty()) {
                                            playerManager.setPlaylist(albumTracks)
                                            playerManager.play(albumTracks.first())
                                        }
                                    }
                                )
                            }
                            "downloads" -> {
                                DownloadsScreen(
                                    tracks = tracks,
                                    playbackState = playbackState,
                                    onTrackClick = { playerManager.play(it) },
                                    onDeleteClick = { repository.deleteTrack(it) },
                                    onPlayArtistClick = { artist ->
                                        val artistTracks = tracks.filter { it.artist == artist && it.isDownloaded }
                                        if (artistTracks.isNotEmpty()) {
                                            playerManager.setPlaylist(artistTracks)
                                            playerManager.play(artistTracks.first())
                                        }
                                    },
                                    onPlayAlbumClick = { album ->
                                        val albumTracks = tracks.filter { it.album == album && it.isDownloaded }
                                        if (albumTracks.isNotEmpty()) {
                                            playerManager.setPlaylist(albumTracks)
                                            playerManager.play(albumTracks.first())
                                        }
                                    }
                                )
                            }
                        }

                        // Floating Player Bar if a track is loaded
                        if (playbackState.currentTrack != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                            ) {
                                PlayerBar(
                                    state = playbackState,
                                    onPlayPauseClick = { playerManager.togglePlay() },
                                    onNextClick = { playerManager.next() },
                                    onPrevClick = { playerManager.prev() },
                                    onSeek = { playerManager.seekTo(it) },
                                    onShuffleClick = { playerManager.toggleShuffle() },
                                    onRepeatClick = { playerManager.toggleRepeat() },
                                    onVolumeChange = { playerManager.setVolume(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        playerManager.release()
        super.onDestroy()
    }
}
