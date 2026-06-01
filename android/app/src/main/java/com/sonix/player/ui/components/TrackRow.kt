package com.sonix.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix.player.data.Track
import com.sonix.player.ui.theme.CyanSecondary
import com.sonix.player.ui.theme.PinkTertiary
import com.sonix.player.ui.theme.VioletPrimary

@Composable
fun TrackRow(
    track: Track,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    onTrackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPlaylistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Frosted glass appearance
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable { onTrackClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Track Art / Icon with gradient background
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isCurrentTrack) {
                                listOf(VioletPrimary, CyanSecondary)
                            } else {
                                listOf(Color(0xFF2E3B5B), Color(0xFF192239))
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentTrack && isPlaying) {
                    // Quick pulse visualizer representation
                    val pulseTransition = rememberInfiniteTransition(label = "pulse")
                    val scale by pulseTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Playing",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp * scale)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Music",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Track details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    color = if (isCurrentTrack) CyanSecondary else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${track.artist} • ${track.album}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration and download controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = track.duration,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                if (onPlaylistClick != null) {
                    IconButton(
                        onClick = onPlaylistClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Adicionar à Playlist",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (track.isDownloading) {
                    // Spinner
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotation),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = PinkTertiary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (track.isDownloaded) {
                    // Downloaded -> Show Delete icon on click
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = PinkTertiary.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Download button
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
