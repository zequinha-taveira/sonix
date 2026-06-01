package com.vibeflow.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MusicRepository(private val context: Context) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val originalTracks = listOf(
        Track("1", "Ambient Waves", "Helix Instrumental", "SoundHelix Vol. 1", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", "6:12"),
        Track("2", "Cybernetic Breeze", "Synth Lounge", "SoundHelix Vol. 2", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", "7:05"),
        Track("3", "Urban Pulse", "Lofi Beats", "SoundHelix Vol. 3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", "5:44"),
        Track("4", "Solar Wind", "Space Odyssey", "SoundHelix Vol. 4", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", "5:02"),
        Track("5", "Neon Sunset", "Retro Synth", "SoundHelix Vol. 5", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3", "6:03")
    )

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    init {
        refreshDownloadStates()
    }

    fun refreshDownloadStates() {
        val updated = originalTracks.map { track ->
            val file = getLocalFile(track)
            if (file.exists() && file.length() > 0) {
                track.copy(isDownloaded = true, localPath = file.absolutePath)
            } else {
                track.copy(isDownloaded = false, localPath = null)
            }
        }
        _tracks.value = updated
    }

    private fun getLocalFile(track: Track): File {
        val folder = File(context.filesDir, "downloads")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return File(folder, "track_${track.id}.mp3")
    }

    fun downloadTrack(track: Track) {
        val currentList = _tracks.value
        val isAlreadyDownloading = currentList.find { it.id == track.id }?.isDownloading == true
        if (isAlreadyDownloading) return

        _tracks.value = currentList.map {
            if (it.id == track.id) it.copy(isDownloading = true) else it
        }

        coroutineScope.launch {
            try {
                val file = getLocalFile(track)
                if (file.exists()) {
                    file.delete()
                }

                withContext(Dispatchers.IO) {
                    val urlConnection = URL(track.url).openConnection() as HttpURLConnection
                    urlConnection.connect()
                    
                    if (urlConnection.responseCode == HttpURLConnection.HTTP_OK) {
                        urlConnection.inputStream.use { input ->
                            FileOutputStream(file).use { output ->
                                val buffer = ByteArray(4096)
                                var bytesRead: Int
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                }
                            }
                        }
                    } else {
                        throw Exception("HTTP error code: ${urlConnection.responseCode}")
                    }
                }

                _tracks.value = _tracks.value.map {
                    if (it.id == track.id) {
                        it.copy(isDownloaded = true, isDownloading = false, localPath = file.absolutePath)
                    } else {
                        it
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _tracks.value = _tracks.value.map {
                    if (it.id == track.id) {
                        it.copy(isDownloading = false, isDownloaded = false)
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun deleteTrack(track: Track) {
        coroutineScope.launch {
            val file = getLocalFile(track)
            if (file.exists()) {
                file.delete()
            }
            _tracks.value = _tracks.value.map {
                if (it.id == track.id) {
                    it.copy(isDownloaded = false, isDownloading = false, localPath = null)
                } else {
                    it
                }
            }
        }
    }
}
