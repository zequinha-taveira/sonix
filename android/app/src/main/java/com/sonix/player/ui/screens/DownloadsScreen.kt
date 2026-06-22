package com.sonix.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix.player.data.Track
import com.sonix.player.player.PlaybackState
import com.sonix.player.ui.components.TrackRow
import com.sonix.player.ui.theme.DarkBackground
import com.sonix.player.ui.theme.VioletPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    tracks: List<Track>,
    playbackState: PlaybackState,
    onTrackClick: (Track) -> Unit,
    onDeleteClick: (Track) -> Unit,
    onPlayArtistClick: (String) -> Unit,
    onPlayAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadedTracks = remember(tracks) { tracks.filter { it.isDownloaded } }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val matchingArtists = remember(downloadedTracks, searchQuery) {
        if (searchQuery.isBlank()) emptyList<String>()
        else downloadedTracks.map { it.artist }.distinct().filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val matchingAlbums = remember(downloadedTracks, searchQuery) {
        if (searchQuery.isBlank()) emptyList<Pair<String, String>>()
        else downloadedTracks.map { it.album to it.artist }.distinctBy { it.first }.filter { it.first.contains(searchQuery, ignoreCase = true) }
    }

    val matchingTracks = remember(downloadedTracks, searchQuery) {
        if (searchQuery.isBlank()) downloadedTracks
        else downloadedTracks.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Músicas Baixadas",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Escute offline sem anúncios",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (downloadedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = "No downloads",
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Biblioteca Vazia",
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Baixe músicas na aba Explorar para escutar quando estiver offline.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Search Bar for downloads with Frosted Glass look
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Pesquisar músicas baixadas...", color = Color.White.copy(alpha = 0.5f)) },
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

            Spacer(modifier = Modifier.height(16.dp))

            if (searchQuery.isBlank()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp) // Padding for PlayerBar
                ) {
                    items(downloadedTracks, key = { it.id }) { track ->
                        val isCurrent = playbackState.currentTrack?.id == track.id
                        TrackRow(
                            track = track,
                            isPlaying = playbackState.isPlaying,
                            isCurrentTrack = isCurrent,
                            onTrackClick = { onTrackClick(track) },
                            onDownloadClick = {}, // Not needed on downloads screen
                            onDeleteClick = { onDeleteClick(track) }
                        )
                    }
                }
            } else {
                if (matchingArtists.isEmpty() && matchingAlbums.isEmpty() && matchingTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhuma música encontrada",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 120.dp) // Padding for PlayerBar
                    ) {
                        // Artists Section
                        if (matchingArtists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Artistas",
                                    fontSize = 16.sp,
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
                                    text = "Álbuns",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(matchingAlbums) { (album, artist) ->
                                AlbumRow(album = album, artist = artist, onClick = { onPlayAlbumClick(album) })
                            }
                        }

                        // Tracks Section
                        if (matchingTracks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Músicas",
                                    fontSize = 16.sp,
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
                                    onDownloadClick = {},
                                    onDeleteClick = { onDeleteClick(track) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

