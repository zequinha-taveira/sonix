package com.sonix.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.CloudOff
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.sonix.player.data.MusicRepository
import com.sonix.player.data.Playlist
import com.sonix.player.data.Track
import com.sonix.player.player.AudioPlayerManager
import com.sonix.player.ui.components.PlayerBar
import com.sonix.player.ui.screens.DownloadsScreen
import com.sonix.player.ui.screens.ExploreScreen
import com.sonix.player.ui.screens.PlaylistsScreen
import com.sonix.player.ui.theme.CyanSecondary
import com.sonix.player.ui.theme.DarkSurface
import com.sonix.player.ui.theme.PinkTertiary
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
                var currentTab by rememberSaveable { mutableStateOf("explore") }
                val tracks by repository.tracks.collectAsState()
                val onlineSearchResults by repository.onlineSearchResults.collectAsState()
                val isSearchingOnline by repository.isSearchingOnline.collectAsState()
                val onlineSearchError by repository.onlineSearchError.collectAsState()
                val playlists by repository.playlists.collectAsState()
                val isSyncing by repository.isSyncing.collectAsState()
                val playbackState by playerManager.playbackState.collectAsState()
                val isOnline by repository.isOnline.collectAsState()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ -> }

                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                LaunchedEffect(playbackState.error) {
                    playbackState.error?.let { errorMsg ->
                        android.widget.Toast.makeText(applicationContext, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                    }
                }

                var showPlaylistPickerDialog by rememberSaveable { mutableStateOf(false) }
                var trackToAddToPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
                val trackToAddToPlaylist = remember(trackToAddToPlaylistId, tracks, onlineSearchResults) {
                    if (trackToAddToPlaylistId == null) null
                    else (tracks + onlineSearchResults).find { it.id == trackToAddToPlaylistId }
                }

                // Dialog modal to select target playlist
                if (showPlaylistPickerDialog && trackToAddToPlaylist != null) {
                    val track = trackToAddToPlaylist
                    AlertDialog(
                        onDismissRequest = {
                            showPlaylistPickerDialog = false
                            trackToAddToPlaylistId = null
                        },
                        title = {
                            Text(
                                text = "Adicionar à Playlist",
                                color = Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Adicionar \"${track.title}\" a:",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (playlists.isEmpty()) {
                                    Text(
                                        text = "Nenhuma playlist criada. Crie uma abaixo ou na aba Playlists!",
                                        color = PinkTertiary,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.heightIn(max = 160.dp)
                                    ) {
                                        items(playlists) { playlist ->
                                            val hasTrack = playlist.trackIds.contains(track.id)
                                            val itemColor = if (hasTrack) CyanSecondary else Color.White

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        if (hasTrack) {
                                                            repository.removeTrackFromPlaylist(playlist.id, track.id)
                                                        } else {
                                                            repository.addTrackToPlaylist(playlist.id, track.id)
                                                        }
                                                        showPlaylistPickerDialog = false
                                                        trackToAddToPlaylistId = null
                                                    }
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = playlist.name,
                                                    color = itemColor,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                    fontSize = 15.sp
                                                )
                                                if (hasTrack) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Adicionado",
                                                        tint = CyanSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                var newPlaylistName by remember { mutableStateOf("") }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newPlaylistName,
                                        onValueChange = { newPlaylistName = it },
                                        placeholder = { Text("Nova playlist...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyanSecondary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            if (newPlaylistName.isNotBlank()) {
                                                val id = "playlist_${System.currentTimeMillis()}"
                                                repository.savePlaylist(Playlist(id, newPlaylistName.trim(), listOf(track.id)))
                                                showPlaylistPickerDialog = false
                                                trackToAddToPlaylistId = null
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyanSecondary,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Text("Criar", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPlaylistPickerDialog = false
                                    trackToAddToPlaylistId = null
                                }
                            ) {
                                Text("Fechar", color = Color.White)
                            }
                        },
                        containerColor = Color(0xFF0F111A),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    )
                }

                // Keep playback manager playlist up to date when tracks list updates (e.g., download complete)
                LaunchedEffect(tracks, onlineSearchResults) {
                    playerManager.setPlaylist((tracks + onlineSearchResults).distinctBy { it.id })
                }
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val screenWidthDp = configuration.screenWidthDp
                val isTabletOrLandscape = isLandscape || screenWidthDp >= 600

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (!isTabletOrLandscape) {
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
                                NavigationBarItem(
                                    selected = currentTab == "playlists",
                                    onClick = { currentTab = "playlists" },
                                    icon = { Icon(Icons.Default.List, contentDescription = "Playlists") },
                                    label = { Text("Playlists") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = VioletPrimary,
                                        selectedTextColor = VioletPrimary,
                                        indicatorColor = Color.White.copy(alpha = 0.1f),
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isTabletOrLandscape) {
                            NavigationRail(
                                containerColor = DarkSurface.copy(alpha = 0.9f),
                                contentColor = Color.White,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Sonix",
                                    color = VioletPrimary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                NavigationRailItem(
                                    selected = currentTab == "explore",
                                    onClick = { currentTab = "explore" },
                                    icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                                    label = { Text("Explorar") },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = VioletPrimary,
                                        selectedTextColor = VioletPrimary,
                                        indicatorColor = Color.White.copy(alpha = 0.1f),
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                    )
                                )
                                NavigationRailItem(
                                    selected = currentTab == "downloads",
                                    onClick = { currentTab = "downloads" },
                                    icon = { Icon(Icons.Default.DownloadDone, contentDescription = "Downloads") },
                                    label = { Text("Downloads") },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = CyanSecondary,
                                        selectedTextColor = CyanSecondary,
                                        indicatorColor = Color.White.copy(alpha = 0.1f),
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                    )
                                )
                                NavigationRailItem(
                                    selected = currentTab == "playlists",
                                    onClick = { currentTab = "playlists" },
                                    icon = { Icon(Icons.Default.List, contentDescription = "Playlists") },
                                    label = { Text("Playlists") },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = VioletPrimary,
                                        selectedTextColor = VioletPrimary,
                                        indicatorColor = Color.White.copy(alpha = 0.1f),
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            if (!isOnline) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFFE57373).copy(alpha = 0.9f),
                                                    Color(0xFFEF5350).copy(alpha = 0.9f)
                                                )
                                            )
                                        )
                                        .padding(vertical = 8.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudOff,
                                            contentDescription = "Offline",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Sem conexão com a internet. Modo Offline ativo.",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                // Switch between Screens
                                when (currentTab) {
                                    "explore" -> {
                                        ExploreScreen(
                                            tracks = tracks,
                                            onlineSearchResults = onlineSearchResults,
                                            isSearchingOnline = isSearchingOnline,
                                            onlineSearchError = onlineSearchError,
                                            isSyncing = isSyncing,
                                            playbackState = playbackState,
                                            onSearchOnline = { query, src -> repository.searchOnline(query, src) },
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
                                            },
                                            onAddToPlaylistClick = {
                                                trackToAddToPlaylistId = it.id
                                                showPlaylistPickerDialog = true
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
                                    "playlists" -> {
                                        PlaylistsScreen(
                                            playlists = playlists,
                                            tracks = (tracks + onlineSearchResults).distinctBy { it.id },
                                            playbackState = playbackState,
                                            onCreatePlaylist = { name ->
                                                val id = "playlist_${System.currentTimeMillis()}"
                                                repository.savePlaylist(Playlist(id, name, emptyList()))
                                            },
                                            onDeletePlaylist = { id -> repository.deletePlaylist(id) },
                                            onTrackClick = { playerManager.play(it) },
                                            onRemoveTrackFromPlaylist = { playlistId, trackId ->
                                                repository.removeTrackFromPlaylist(playlistId, trackId)
                                            },
                                            onPlayPlaylist = { playlist ->
                                                val playlistTracks = (tracks + onlineSearchResults).distinctBy { it.id }.filter { playlist.trackIds.contains(it.id) }
                                                if (playlistTracks.isNotEmpty()) {
                                                    playerManager.setPlaylist(playlistTracks)
                                                    playerManager.play(playlistTracks.first())
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
                                            onVolumeChange = { playerManager.setVolume(it) },
                                            isLandscape = isTabletOrLandscape
                                        )
                                    }
                                }
                            }
                        }
                    }
                }}
            }
        }
    }

    override fun onDestroy() {
        playerManager.release()
        super.onDestroy()
    }
}
