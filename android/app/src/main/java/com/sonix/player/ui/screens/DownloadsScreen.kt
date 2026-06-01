package com.sonix.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix.player.data.Track
import com.sonix.player.player.PlaybackState
import com.sonix.player.ui.components.TrackRow
import com.sonix.player.ui.theme.DarkBackground
import com.sonix.player.ui.theme.PinkTertiary

@Composable
fun DownloadsScreen(
    tracks: List<Track>,
    playbackState: PlaybackState,
    onTrackClick: (Track) -> Unit,
    onDeleteClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadedTracks = tracks.filter { it.isDownloaded }

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
        }
    }
}
