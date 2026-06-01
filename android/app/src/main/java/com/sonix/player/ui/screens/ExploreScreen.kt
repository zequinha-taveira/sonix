package com.sonix.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix.player.data.Track
import com.sonix.player.player.PlaybackState
import com.sonix.player.ui.components.TrackRow
import com.sonix.player.ui.theme.CyanSecondary
import com.sonix.player.ui.theme.DarkBackground
import com.sonix.player.ui.theme.PinkTertiary
import com.sonix.player.ui.theme.VioletPrimary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    tracks: List<Track>,
    onlineSearchResults: List<Track>,
    isSearchingOnline: Boolean,
    onlineSearchError: String?,
    isSyncing: Boolean,
    playbackState: PlaybackState,
    onSearchOnline: (String, String) -> Unit,
    onClearOnlineSearch: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onDownloadClick: (Track) -> Unit,
    onDeleteClick: (Track) -> Unit,
    onPlayArtistClick: (String) -> Unit,
    onPlayAlbumClick: (String) -> Unit,
    onAddToPlaylistClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSourceFilter by remember { mutableStateOf("Todas as Origens") }
    var selectedTypeFilter by remember { mutableStateOf("Tudo") }

    LaunchedEffect(searchQuery, selectedSourceFilter) {
        if (searchQuery.isNotBlank()) {
            delay(300)
            onSearchOnline(searchQuery, selectedSourceFilter)
        } else {
            onClearOnlineSearch()
        }
    }

    val renderArtists = remember(selectedTypeFilter) { selectedTypeFilter == "Tudo" || selectedTypeFilter == "Artistas" }
    val renderAlbums = remember(selectedTypeFilter) { selectedTypeFilter == "Tudo" || selectedTypeFilter == "Álbuns" }
    val renderTracks = remember(selectedTypeFilter) { selectedTypeFilter == "Tudo" || selectedTypeFilter == "Músicas" }

    val matchingArtists = remember(tracks, searchQuery, renderArtists) {
        if (!renderArtists || searchQuery.isBlank()) emptyList<String>()
        else tracks.map { it.artist }.distinct().filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val matchingAlbums = remember(tracks, searchQuery, renderAlbums) {
        if (!renderAlbums || searchQuery.isBlank()) emptyList<Pair<String, String>>()
        else tracks.map { it.album to it.artist }.distinctBy { it.first }.filter { it.first.contains(searchQuery, ignoreCase = true) }
    }

    val matchingTracks = remember(tracks, searchQuery, renderTracks) {
        if (!renderTracks) emptyList<Track>()
        else if (searchQuery.isBlank()) tracks
        else tracks.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        // Upper Title & Cloud Sync status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Explorar",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                val syncText = if (isSyncing) "Sincronizando..." else "Sincronizado"
                val syncColor = if (isSyncing) CyanSecondary else Color(0xFF10B981)

                if (isSyncing) {
                    CircularProgressIndicator(
                        color = syncColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Sincronizado",
                        tint = syncColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = syncText,
                    color = syncColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            VioletPrimary.copy(alpha = 0.8f),
                            PinkTertiary.copy(alpha = 0.6f),
                            CyanSecondary.copy(alpha = 0.5f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                .padding(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(
                    text = "Sonix Premium",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Busque, crie playlists e ouça offline sem interrupções",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Pesquisar músicas, artistas...", color = Color.White.copy(alpha = 0.5f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White.copy(alpha = 0.6f)) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = VioletPrimary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Rows Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Source Filters (Only show when searching online)
            if (searchQuery.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Origem: ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.width(50.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val sources = listOf("Todas as Origens", "iTunes", "Deezer")
                        items(sources) { src ->
                            CustomFilterChip(
                                text = if (src == "Todas as Origens") "Todas" else src,
                                selected = selectedSourceFilter == src,
                                onClick = { selectedSourceFilter = src }
                            )
                        }
                    }
                }
            }

            // Content Type Filters
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tipo: ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.width(50.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val types = listOf("Tudo", "Músicas", "Artistas", "Álbuns")
                    items(types) { typ ->
                        CustomFilterChip(
                            text = typ,
                            selected = selectedTypeFilter == typ,
                            onClick = { selectedTypeFilter = typ }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (searchQuery.isBlank()) {
            if (selectedTypeFilter != "Tudo" && selectedTypeFilter != "Músicas") {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Use a busca para filtrar por artistas ou álbuns",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                }
            } else {
                Text(
                    text = "Músicas Disponíveis",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        val isCurrent = playbackState.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            isPlaying = playbackState.isPlaying,
                            isCurrentTrack = isCurrent,
                            onTrackClick = { onTrackClick(track) },
                            onDownloadClick = { onDownloadClick(track) },
                            onDeleteClick = { onDeleteClick(track) },
                            onPlaylistClick = { onAddToPlaylistClick(track) }
                        )
                    }
                }
            }
        } else {
            if (matchingArtists.isEmpty() && matchingAlbums.isEmpty() && matchingTracks.isEmpty() && onlineSearchResults.isEmpty() && !isSearchingOnline) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (onlineSearchError != null) "Sem conexão com a internet" else "Nenhum resultado encontrado",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Artists Section
                    if (matchingArtists.isNotEmpty()) {
                        item {
                            Text(
                                text = "Artistas Locais",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(matchingArtists) { artist ->
                            ArtistRow(artist = artist, onClick = { onPlayArtistClick(artist) })
                        }
                    }

                    // Albums Section
                    if (matchingAlbums.isNotEmpty()) {
                        item {
                            Text(
                                text = "Álbuns Locais",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(matchingAlbums) { (album, artist) ->
                            AlbumRow(album = album, artist = artist, onClick = { onPlayAlbumClick(album) })
                        }
                    }

                    // Local Tracks Section
                    if (matchingTracks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Músicas Locais",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(matchingTracks, key = { it.id }) { track ->
                            val isCurrent = playbackState.currentTrack?.id == track.id
                            TrackRow(
                                track = track,
                                isPlaying = playbackState.isPlaying,
                                isCurrentTrack = isCurrent,
                                onTrackClick = { onTrackClick(track) },
                                onDownloadClick = { onDownloadClick(track) },
                                onDeleteClick = { onDeleteClick(track) },
                                onPlaylistClick = { onAddToPlaylistClick(track) }
                            )
                        }
                    }

                    // Online Results Section
                    if (renderTracks) {
                        item {
                            Text(
                                text = "Resultados Online",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        if (isSearchingOnline) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = VioletPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        } else if (onlineSearchError != null) {
                            item {
                                Text(
                                    text = "Sem conexão com a internet.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else if (onlineSearchResults.isEmpty()) {
                            item {
                                Text(
                                    text = "Nenhuma música online encontrada.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            items(onlineSearchResults, key = { it.id }) { track ->
                                val isCurrent = playbackState.currentTrack?.id == track.id
                                TrackRow(
                                    track = track,
                                    isPlaying = playbackState.isPlaying,
                                    isCurrentTrack = isCurrent,
                                    onTrackClick = { onTrackClick(track) },
                                    onDownloadClick = { onDownloadClick(track) },
                                    onDeleteClick = { onDeleteClick(track) },
                                    onPlaylistClick = { onAddToPlaylistClick(track) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    Brush.linearGradient(colors = listOf(VioletPrimary, CyanSecondary))
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
