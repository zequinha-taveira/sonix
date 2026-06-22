package com.sonix.player.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sonix.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PlaybackRepeatMode {
    OFF, ONE, ALL
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 1.0f,
    val isShuffle: Boolean = false,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
    val error: String? = null
)

class AudioPlayerManager(private val context: Context) {
    private val player = ExoPlayer.Builder(context).build()
    
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var playlist: List<Track> = emptyList()
    private var progressJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _playbackState.value = _playbackState.value.copy(durationMs = player.duration)
                } else if (state == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val message = if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                    "Falha na conexão de rede. Verifique sua internet."
                } else {
                    "Erro de reprodução: ${error.localizedMessage}"
                }
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    error = message
                )
            }
        })
    }

    fun setPlaylist(tracks: List<Track>) {
        this.playlist = tracks
    }

    fun play(track: Track) {
        val path = if (track.isDownloaded && track.localPath != null) {
            track.localPath
        } else {
            track.url
        }

        val mediaItem = MediaItem.fromUri(Uri.parse(path))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        _playbackState.value = _playbackState.value.copy(
            currentTrack = track,
            progressMs = 0,
            durationMs = 0,
            error = null
        )
    }

    fun togglePlay() {
        val current = _playbackState.value.currentTrack
        if (current == null && playlist.isNotEmpty()) {
            play(playlist.first())
            return
        }

        if (player.isPlaying) {
            player.pause()
        } else {
            _playbackState.value = _playbackState.value.copy(error = null)
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(progressMs = positionMs)
    }

    fun setVolume(volume: Float) {
        player.volume = volume
        _playbackState.value = _playbackState.value.copy(volume = volume)
    }

    fun next() {
        val current = _playbackState.value.currentTrack ?: return
        if (playlist.isEmpty()) return

        val currentIndex = playlist.indexOfFirst { it.id == current.id }
        if (currentIndex == -1) return

        val nextIndex = if (_playbackState.value.isShuffle) {
            playlist.indices.random()
        } else {
            (currentIndex + 1) % playlist.size
        }
        play(playlist[nextIndex])
    }

    fun prev() {
        val current = _playbackState.value.currentTrack ?: return
        if (playlist.isEmpty()) return

        val currentIndex = playlist.indexOfFirst { it.id == current.id }
        if (currentIndex == -1) return

        var prevIndex = currentIndex - 1
        if (prevIndex < 0) {
            prevIndex = playlist.size - 1
        }
        play(playlist[prevIndex])
    }

    fun toggleShuffle() {
        val newShuffle = !_playbackState.value.isShuffle
        _playbackState.value = _playbackState.value.copy(isShuffle = newShuffle)
    }

    fun toggleRepeat() {
        val currentMode = _playbackState.value.repeatMode
        val nextMode = when (currentMode) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
        _playbackState.value = _playbackState.value.copy(repeatMode = nextMode)
    }

    private fun handleTrackEnded() {
        when (_playbackState.value.repeatMode) {
            PlaybackRepeatMode.ONE -> {
                player.seekTo(0)
                player.play()
            }
            PlaybackRepeatMode.ALL -> {
                next()
            }
            PlaybackRepeatMode.OFF -> {
                val current = _playbackState.value.currentTrack
                if (current != null && playlist.isNotEmpty()) {
                    val currentIndex = playlist.indexOfFirst { it.id == current.id }
                    if (currentIndex != -1 && currentIndex < playlist.size - 1) {
                        next()
                    } else {
                        player.pause()
                        player.seekTo(0)
                        _playbackState.value = _playbackState.value.copy(isPlaying = false, progressMs = 0)
                    }
                }
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = coroutineScope.launch {
            while (true) {
                _playbackState.value = _playbackState.value.copy(progressMs = player.currentPosition)
                delay(1000)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracker()
        player.release()
    }
}
