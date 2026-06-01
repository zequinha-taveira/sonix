package com.sonix.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MusicRepository(private val context: Context) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val aggregator = SearchAggregator(listOf(ITunesSearchProvider(), DeezerSearchProvider()))

    private val originalTracks = listOf(
        Track("1", "Ambient Waves", "Helix Instrumental", "SoundHelix Vol. 1", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", "6:12"),
        Track("2", "Cybernetic Breeze", "Synth Lounge", "SoundHelix Vol. 2", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", "7:05"),
        Track("3", "Urban Pulse", "Lofi Beats", "SoundHelix Vol. 3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", "5:44"),
        Track("4", "Solar Wind", "Space Odyssey", "SoundHelix Vol. 4", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", "5:02"),
        Track("5", "Neon Sunset", "Retro Synth", "SoundHelix Vol. 5", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3", "6:03")
    )

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _onlineSearchResults = MutableStateFlow<List<Track>>(emptyList())
    val onlineSearchResults: StateFlow<List<Track>> = _onlineSearchResults.asStateFlow()

    private val _isSearchingOnline = MutableStateFlow(false)
    val isSearchingOnline: StateFlow<Boolean> = _isSearchingOnline.asStateFlow()

    private val _onlineSearchError = MutableStateFlow<String?>(null)
    val onlineSearchError: StateFlow<String?> = _onlineSearchError.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var currentSearchJob: Job? = null

    init {
        refreshDownloadStates()
        loadPlaylists()
    }

    fun refreshDownloadStates() {
        val downloadedMetadata = getDownloadedTracksMetadata()
        val allPossibleTracks = (originalTracks + downloadedMetadata).distinctBy { it.id }

        val updated = allPossibleTracks.map { track ->
            val file = getLocalFile(track)
            if (file.exists() && file.length() > 0) {
                track.copy(isDownloaded = true, localPath = file.absolutePath)
            } else {
                track.copy(isDownloaded = false, localPath = null)
            }
        }

        // Only keep original tracks or tracks that are actually downloaded
        val filtered = updated.filter { it.id in originalTracks.map { ot -> ot.id } || it.isDownloaded }
        _tracks.value = filtered
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

        val updatedList = if (currentList.any { it.id == track.id }) {
            currentList.map { if (it.id == track.id) it.copy(isDownloading = true) else it }
        } else {
            currentList + track.copy(isDownloading = true)
        }
        _tracks.value = updatedList

        _onlineSearchResults.value = _onlineSearchResults.value.map {
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

                if (originalTracks.none { it.id == track.id }) {
                    saveDownloadedTrackMetadata(track)
                }

                refreshDownloadStates()
                triggerCloudSync()

                _onlineSearchResults.value = _onlineSearchResults.value.map {
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
                _onlineSearchResults.value = _onlineSearchResults.value.map {
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
            if (originalTracks.none { it.id == track.id }) {
                deleteDownloadedTrackMetadata(track.id)
            }
            refreshDownloadStates()
            triggerCloudSync()

            _onlineSearchResults.value = _onlineSearchResults.value.map {
                if (it.id == track.id) {
                    it.copy(isDownloaded = false, isDownloading = false, localPath = null)
                } else {
                    it
                }
            }
        }
    }

    // --- iTunes Online Search ---

    fun searchOnline(query: String, allowedSource: String = "Todas as Origens") {
        currentSearchJob?.cancel()
        _onlineSearchError.value = null
        if (query.isBlank()) {
            _onlineSearchResults.value = emptyList()
            _isSearchingOnline.value = false
            return
        }

        _isSearchingOnline.value = true
        currentSearchJob = coroutineScope.launch {
            try {
                val results = aggregator.search(query, allowedSource)
                val updatedResults = results.map { track ->
                    val file = getLocalFile(track)
                    if (file.exists() && file.length() > 0) {
                        track.copy(isDownloaded = true, localPath = file.absolutePath)
                    } else {
                        track.copy(isDownloaded = false, localPath = null)
                    }
                }
                _onlineSearchResults.value = updatedResults
            } catch (e: Exception) {
                e.printStackTrace()
                _onlineSearchResults.value = emptyList()
                _onlineSearchError.value = "Erro de conexão"
            } finally {
                _isSearchingOnline.value = false
            }
        }
    }

    fun clearOnlineSearch() {
        currentSearchJob?.cancel()
        _onlineSearchResults.value = emptyList()
        _isSearchingOnline.value = false
        _onlineSearchError.value = null
    }

    // --- SharedPreferences Metadata Caching ---

    private fun saveDownloadedTrackMetadata(track: Track) {
        val sharedPrefs = context.getSharedPreferences("downloaded_tracks_prefs", Context.MODE_PRIVATE)
        val currentTracks = getDownloadedTracksMetadata().toMutableList()
        if (currentTracks.none { it.id == track.id }) {
            currentTracks.add(track)
            val jsonArray = JSONArray()
            currentTracks.forEach { t ->
                val jobj = JSONObject()
                jobj.put("id", t.id)
                jobj.put("title", t.title)
                jobj.put("artist", t.artist)
                jobj.put("album", t.album)
                jobj.put("url", t.url)
                jobj.put("duration", t.duration)
                jsonArray.put(jobj)
            }
            sharedPrefs.edit().putString("tracks_metadata", jsonArray.toString()).apply()
        }
    }

    private fun deleteDownloadedTrackMetadata(trackId: String) {
        val sharedPrefs = context.getSharedPreferences("downloaded_tracks_prefs", Context.MODE_PRIVATE)
        val currentTracks = getDownloadedTracksMetadata().filter { it.id != trackId }
        val jsonArray = JSONArray()
        currentTracks.forEach { t ->
            val jobj = JSONObject()
            jobj.put("id", t.id)
            jobj.put("title", t.title)
            jobj.put("artist", t.artist)
            jobj.put("album", t.album)
            jobj.put("url", t.url)
            jobj.put("duration", t.duration)
            jsonArray.put(jobj)
        }
        sharedPrefs.edit().putString("tracks_metadata", jsonArray.toString()).apply()
    }

    private fun getDownloadedTracksMetadata(): List<Track> {
        val sharedPrefs = context.getSharedPreferences("downloaded_tracks_prefs", Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString("tracks_metadata", null) ?: return emptyList()
        val list = mutableListOf<Track>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                list.add(
                    Track(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        artist = item.getString("artist"),
                        album = item.getString("album"),
                        url = item.getString("url"),
                        duration = item.getString("duration")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun triggerCloudSync() {
        if (_isSyncing.value) return
        coroutineScope.launch {
            _isSyncing.value = true
            kotlinx.coroutines.delay(1500)
            _isSyncing.value = false
        }
    }

    fun loadPlaylists() {
        val sharedPrefs = context.getSharedPreferences("playlists_prefs", Context.MODE_PRIVATE)
        val jsonString = sharedPrefs.getString("playlists_data", null) ?: return
        val list = mutableListOf<Playlist>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val trackIdsArray = item.getJSONArray("trackIds")
                val trackIds = mutableListOf<String>()
                for (j in 0 until trackIdsArray.length()) {
                    trackIds.add(trackIdsArray.getString(j))
                }
                list.add(Playlist(item.getString("id"), item.getString("name"), trackIds))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _playlists.value = list
    }

    fun savePlaylist(playlist: Playlist) {
        val currentList = _playlists.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == playlist.id }
        if (index != -1) {
            currentList[index] = playlist
        } else {
            currentList.add(playlist)
        }
        persistPlaylists(currentList)
        triggerCloudSync()
    }

    fun deletePlaylist(playlistId: String) {
        val currentList = _playlists.value.filter { it.id != playlistId }
        persistPlaylists(currentList)
        triggerCloudSync()
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        val playlist = _playlists.value.firstOrNull { it.id == playlistId } ?: return
        if (!playlist.trackIds.contains(trackId)) {
            val updated = playlist.copy(trackIds = playlist.trackIds + trackId)
            savePlaylist(updated)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val playlist = _playlists.value.firstOrNull { it.id == playlistId } ?: return
        if (playlist.trackIds.contains(trackId)) {
            val updated = playlist.copy(trackIds = playlist.trackIds - trackId)
            savePlaylist(updated)
        }
    }

    private fun persistPlaylists(list: List<Playlist>) {
        val sharedPrefs = context.getSharedPreferences("playlists_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        list.forEach { p ->
            val jobj = JSONObject()
            jobj.put("id", p.id)
            jobj.put("name", p.name)
            val trackIdsArray = JSONArray()
            p.trackIds.forEach { trackIdsArray.put(it) }
            jobj.put("trackIds", trackIdsArray)
            jsonArray.put(jobj)
        }
        sharedPrefs.edit().putString("playlists_data", jsonArray.toString()).apply()
        _playlists.value = list
    }
}

interface MusicSearchProvider {
    val name: String
    suspend fun search(query: String): List<Track>
}

class ITunesSearchProvider : MusicSearchProvider {
    override val name = "iTunes"
    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val urlString = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=10"
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            parseiTunesResponse(responseText)
        } else {
            emptyList()
        }
    }

    private fun parseiTunesResponse(jsonString: String): List<Track> {
        val list = mutableListOf<Track>()
        try {
            val jsonObject = JSONObject(jsonString)
            val resultsArray = jsonObject.getJSONArray("results")
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val trackId = item.optLong("trackId", 0).toString()
                val title = item.optString("trackName", "")
                val artist = item.optString("artistName", "")
                val album = item.optString("collectionName", "Single")
                val previewUrl = item.optString("previewUrl", "")
                val durationMs = item.optLong("trackTimeMillis", 0)
                
                if (trackId != "0" && previewUrl.isNotEmpty()) {
                    val durationStr = formatMillisToTime(durationMs)
                    list.add(
                        Track(
                            id = "itunes_$trackId",
                            title = title,
                            artist = artist,
                            album = album,
                            url = previewUrl,
                            duration = durationStr
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun formatMillisToTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

class DeezerSearchProvider : MusicSearchProvider {
    override val name = "Deezer"
    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val urlString = "https://api.deezer.com/search?q=$encodedQuery&limit=10"
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            parseDeezerResponse(responseText)
        } else {
            emptyList()
        }
    }

    private fun parseDeezerResponse(jsonString: String): List<Track> {
        val list = mutableListOf<Track>()
        try {
            val jsonObject = JSONObject(jsonString)
            val dataArray = jsonObject.getJSONArray("data")
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                val trackId = item.optLong("id", 0).toString()
                val title = item.optString("title", "")
                val artistObj = item.getJSONObject("artist")
                val artist = artistObj.optString("name", "")
                val albumObj = item.getJSONObject("album")
                val album = albumObj.optString("title", "Single")
                val previewUrl = item.optString("preview", "")
                val durationSec = item.optLong("duration", 0)
                
                if (trackId != "0" && previewUrl.isNotEmpty()) {
                    val minutes = durationSec / 60
                    val seconds = durationSec % 60
                    val durationStr = String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
                    list.add(
                        Track(
                            id = "deezer_$trackId",
                            title = title,
                            artist = artist,
                            album = album,
                            url = previewUrl,
                            duration = durationStr
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}

class SearchAggregator(private val providers: List<MusicSearchProvider>) {
    suspend fun search(query: String, allowedSource: String = "Todas as Origens"): List<Track> = withContext(Dispatchers.IO) {
        val filteredProviders = if (allowedSource == "Todas as Origens") {
            providers
        } else {
            providers.filter { it.name.equals(allowedSource, ignoreCase = true) }
        }
        val deferreds = filteredProviders.map { provider ->
            async {
                try {
                    provider.search(query)
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList<Track>()
                }
            }
        }
        val allResults = deferreds.awaitAll().flatten()
        allResults.distinctBy { it.url }.toList()
    }
}
