package com.sonix.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.tooling.preview.Preview
import com.sonix.player.data.Track
import com.sonix.player.player.PlaybackRepeatMode
import com.sonix.player.player.PlaybackState
import com.sonix.player.ui.theme.CyanSecondary
import com.sonix.player.ui.theme.PinkTertiary
import com.sonix.player.ui.theme.VioletPrimary

@Composable
fun PlayerBar(
    state: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentTrack = state.currentTrack ?: return

    var isSliderDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableLongStateOf(0L) }

    val currentPosition = if (isSliderDragging) dragProgress else state.progressMs
    val duration = state.durationMs

    // Time Formatter helper
    fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (isLandscape) 8.dp else 16.dp)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(if (isLandscape) 16.dp else 24.dp)),
        shape = RoundedCornerShape(if (isLandscape) 16.dp else 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF131B2E).copy(alpha = 0.85f)
        )
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Info da Música (esquerda)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(0.28f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(VioletPrimary, PinkTertiary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Playing track art",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = currentTrack.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack.artist,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Controles de Reprodução (centro)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(0.35f)
                ) {
                    IconButton(onClick = onShuffleClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.isShuffle) CyanSecondary else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onPrevClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(VioletPrimary, CyanSecondary)
                                )
                            )
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onNextClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onRepeatClick, modifier = Modifier.size(32.dp)) {
                        val repeatIcon = when (state.repeatMode) {
                            PlaybackRepeatMode.ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        }
                        val tint = when (state.repeatMode) {
                            PlaybackRepeatMode.OFF -> Color.White.copy(alpha = 0.5f)
                            PlaybackRepeatMode.ALL -> CyanSecondary
                            PlaybackRepeatMode.ONE -> PinkTertiary
                        }
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = "Repeat",
                            tint = tint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Barra de Progresso e Mute (direita)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(0.37f)
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )

                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { percent ->
                            isSliderDragging = true
                            dragProgress = (percent * duration).toLong()
                        },
                        onValueChangeFinished = {
                            isSliderDragging = false
                            onSeek(dragProgress)
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = VioletPrimary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                            thumbColor = VioletPrimary
                        )
                    )

                    Text(
                        text = formatTime(duration),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )

                    IconButton(
                        onClick = { onVolumeChange(if (state.volume > 0f) 0f else 0.8f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (state.volume == 0f) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Track Info Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Spinning gradient/glowing art placeholder
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(VioletPrimary, PinkTertiary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Playing track art",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = currentTrack.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack.artist,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Volume Controller
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(100.dp)
                    ) {
                        Icon(
                            imageVector = if (state.volume == 0f) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onVolumeChange(if (state.volume > 0f) 0f else 0.8f) }
                        )
                        Slider(
                            value = state.volume,
                            onValueChange = onVolumeChange,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                activeTrackColor = CyanSecondary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                                thumbColor = CyanSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Playback progress slider
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { percent ->
                        isSliderDragging = true
                        dragProgress = (percent * duration).toLong()
                    },
                    onValueChangeFinished = {
                        isSliderDragging = false
                        onSeek(dragProgress)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        activeTrackColor = VioletPrimary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        thumbColor = VioletPrimary
                    )
                )

                // Progress labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Playback controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button
                    IconButton(onClick = onShuffleClick) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (state.isShuffle) CyanSecondary else Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // Prev Button
                    IconButton(onClick = onPrevClick) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White
                        )
                    }

                    // Play / Pause Button with solid background
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(VioletPrimary, CyanSecondary)
                                )
                            )
                            .clickable { onPlayPauseClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Next Button
                    IconButton(onClick = onNextClick) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }

                    // Repeat Button
                    IconButton(onClick = onRepeatClick) {
                        val repeatIcon = when (state.repeatMode) {
                            PlaybackRepeatMode.ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        }
                        val tint = when (state.repeatMode) {
                            PlaybackRepeatMode.OFF -> Color.White.copy(alpha = 0.5f)
                            PlaybackRepeatMode.ALL -> CyanSecondary
                            PlaybackRepeatMode.ONE -> PinkTertiary
                        }
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = "Repeat",
                            tint = tint
                        )
                }
            }
        }
    }
}

@Preview(name = "Player Bar - Retrato")
@Composable
fun PlayerBarPortraitPreview() {
    PlayerBar(
        state = PlaybackState(
            currentTrack = Track(
                id = "track_1",
                title = "Like a Stone",
                artist = "Audioslave",
                album = "Audioslave",
                url = "",
                duration = "4:54"
            ),
            isPlaying = true,
            progressMs = 124000L,
            durationMs = 294000L,
            volume = 0.8f
        ),
        onPlayPauseClick = {},
        onNextClick = {},
        onPrevClick = {},
        onSeek = {},
        onShuffleClick = {},
        onRepeatClick = {},
        onVolumeChange = {},
        isLandscape = false
    )
}

@Preview(name = "Player Bar - Paisagem")
@Composable
fun PlayerBarLandscapePreview() {
    PlayerBar(
        state = PlaybackState(
            currentTrack = Track(
                id = "track_1",
                title = "Like a Stone",
                artist = "Audioslave",
                album = "Audioslave",
                url = "",
                duration = "4:54"
            ),
            isPlaying = true,
            progressMs = 124000L,
            durationMs = 294000L,
            volume = 0.8f
        ),
        onPlayPauseClick = {},
        onNextClick = {},
        onPrevClick = {},
        onSeek = {},
        onShuffleClick = {},
        onRepeatClick = {},
        onVolumeChange = {},
        isLandscape = true
    )
}

