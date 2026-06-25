import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val url: String,
    val duration: String
)

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String>
)

interface SearchProvider {
    val name: String
    fun search(query: String): kotlin.js.Promise<List<Track>>
}

class ITunesSearchProvider : SearchProvider {
    override val name = "iTunes"
    override fun search(query: String): kotlin.js.Promise<List<Track>> {
        val encodedQuery = js("encodeURIComponent")(query) as String
        val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=song&limit=10"
        return window.asDynamic().fetch(url).then { response ->
            response.json().then { data ->
                val results = data.results as Array<dynamic>
                results.map { item ->
                    val trackId = item.trackId.toString()
                    val title = item.trackName as String
                    val artist = item.artistName as String
                    val album = (item.collectionName ?: "Single") as String
                    val previewUrl = item.previewUrl as String
                    val durationMs = item.trackTimeMillis as Int
                    val durationStr = formatMillisToTime(durationMs)
                    Track("itunes_$trackId", title, artist, album, previewUrl, durationStr)
                }.toList()
            }
        } as kotlin.js.Promise<List<Track>>
    }
}

class LofiCCProvider : SearchProvider {
    override val name = "LofiCC"
    private val ccTracks = listOf(
        Track("cc1", "Lofi Sunset", "Lofi Dreamer", "Chill Beats Vol. 1", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3", "5:02"),
        Track("cc2", "Study Sessions", "Focus Beats", "Study Companion", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3", "7:35"),
        Track("cc3", "Rainy Coffee", "Lofi Beats", "Coffee & Rain", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3", "5:18"),
        Track("cc4", "Ambient Space", "Cosmic Soundscapes", "Deep Space Lofi", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3", "7:02"),
        Track("cc5", "Midnight Coffee", "Retro Synth", "Retro Chill", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3", "6:58")
    )

    override fun search(query: String): kotlin.js.Promise<List<Track>> {
        val filtered = ccTracks.filter { 
            it.title.lowercase().contains(query.lowercase()) || 
            it.artist.lowercase().contains(query.lowercase()) ||
            it.album.lowercase().contains(query.lowercase())
        }
        return kotlin.js.Promise.resolve(filtered)
    }
}

class DeezerSearchProvider : SearchProvider {
    override val name = "Deezer"
    private val deezerTracks = listOf(
        Track("dz1", "Midnight Drive", "Neon Shallows", "Cyber Run", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3", "5:38"),
        Track("dz2", "Vapor Trail", "Synthwave Kid", "Retro Dreams", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3", "6:11"),
        Track("dz3", "Lost in Citylights", "Electric Pulse", "Urban Nights", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-13.mp3", "4:49")
    )
    override fun search(query: String): kotlin.js.Promise<List<Track>> {
        val filtered = deezerTracks.filter { 
            it.title.lowercase().contains(query.lowercase()) || 
            it.artist.lowercase().contains(query.lowercase()) ||
            it.album.lowercase().contains(query.lowercase())
        }
        return kotlin.js.Promise.resolve(filtered)
    }
}

class SearchAggregator(private val providers: List<SearchProvider>) {
    fun search(query: String, allowedSource: String = "Todas as Origens"): kotlin.js.Promise<List<Track>> {
        val filteredProviders = if (allowedSource == "Todas as Origens") {
            providers
        } else {
            providers.filter { it.name.lowercase() == allowedSource.lowercase() }
        }
        
        val promises = filteredProviders.map { provider ->
            provider.search(query).`catch` { err ->
                console.error("Provider ${provider.name} failed:", err)
                emptyList<Track>()
            }
        }.toTypedArray()
        
        return kotlin.js.Promise.all(promises).then { resultsArray ->
            val allTracks = mutableListOf<Track>()
            val results = resultsArray as Array<List<Track>>
            results.forEach { list ->
                allTracks.addAll(list)
            }
            allTracks.distinctBy { it.url }.toList()
        }
    }
}

object CloudSyncManager {
    var isSyncing = false
    var isSynced = true
    private val syncListeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        syncListeners.add(listener)
    }

    fun triggerSync() {
        if (isSyncing) return
        isSyncing = true
        isSynced = false
        notifyListeners()

        window.setTimeout({
            isSyncing = false
            isSynced = true
            notifyListeners()
        }, 1500)
    }

    private fun notifyListeners() {
        syncListeners.forEach { it() }
    }
}

object PlaylistManager {
    fun getPlaylists(): List<Playlist> {
        val json = window.localStorage.getItem("user_playlists") ?: return emptyList()
        return try {
            val parsed = JSON.parse<Array<dynamic>>(json)
            parsed.map { item ->
                val trackIdsArr = item.trackIds as Array<dynamic>
                val trackIdsList = trackIdsArr.map { it as String }.toList()
                Playlist(
                    item.id as String,
                    item.name as String,
                    trackIdsList
                )
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePlaylist(playlist: Playlist) {
        val playlists = getPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlist.id }
        if (index != -1) {
            playlists[index] = playlist
        } else {
            playlists.add(playlist)
        }
        saveAll(playlists)
        CloudSyncManager.triggerSync()
    }

    fun deletePlaylist(id: String) {
        val playlists = getPlaylists().filter { it.id != id }
        saveAll(playlists)
        CloudSyncManager.triggerSync()
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        val playlists = getPlaylists()
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
        if (!playlist.trackIds.contains(trackId)) {
            val updated = playlist.copy(trackIds = playlist.trackIds + trackId)
            savePlaylist(updated)
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val playlists = getPlaylists()
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
        val updated = playlist.copy(trackIds = playlist.trackIds - trackId)
        savePlaylist(updated)
    }

    private fun saveAll(list: List<Playlist>) {
        val rawList = list.map { playlist ->
            val obj = js("{}")
            obj.id = playlist.id
            obj.name = playlist.name
            val idsArr = playlist.trackIds.toTypedArray()
            obj.trackIds = idsArr
            obj
        }.toTypedArray()
        window.localStorage.setItem("user_playlists", JSON.stringify(rawList))
    }
}

object LocalStorageManager {
    fun saveDownloadedTrack(track: Track) {
        val list = getDownloadedTracks().toMutableList()
        if (list.none { it.id == track.id }) {
            list.add(track)
            saveList(list)
            CloudSyncManager.triggerSync()
        }
    }

    fun removeDownloadedTrack(trackId: String) {
        val list = getDownloadedTracks().filter { it.id != trackId }
        saveList(list)
        CloudSyncManager.triggerSync()
    }

    fun getDownloadedTracks(): List<Track> {
        val json = window.localStorage.getItem("downloaded_tracks_metadata") ?: return emptyList()
        return try {
            val parsed = JSON.parse<Array<dynamic>>(json)
            parsed.map { item ->
                Track(
                    item.id as String,
                    item.title as String,
                    item.artist as String,
                    item.album as String,
                    item.url as String,
                    item.duration as String
                )
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveList(list: List<Track>) {
        val rawList = list.map { track ->
            val obj = js("{}")
            obj.id = track.id
            obj.title = track.title
            obj.artist = track.artist
            obj.album = track.album
            obj.url = track.url
            obj.duration = track.duration
            obj
        }.toTypedArray()
        window.localStorage.setItem("downloaded_tracks_metadata", JSON.stringify(rawList))
    }
}

fun formatMillisToTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val secondsStr = if (seconds < 10) "0$seconds" else "$seconds"
    return "$minutes:$secondsStr"
}


enum class View {
    EXPLORE, DOWNLOADS, VISUALIZER, PLAYLISTS
}

enum class RepeatMode {
    OFF, ONE, ALL
}

object OfflineManager {
    const val CACHE_NAME = "sonix-tracks-v1"

    fun isDownloaded(url: String, callback: (Boolean) -> Unit) {
        val caches = window.asDynamic().caches
        if (caches == null) {
            callback(false)
            return
        }
        caches.open(CACHE_NAME).then { cache ->
            cache.match(url).then { response ->
                callback(response != null)
            }
        }.`catch` {
            callback(false)
        }
    }

    fun downloadTrack(url: String, onProgress: () -> Unit, onComplete: (Boolean) -> Unit) {
        val caches = window.asDynamic().caches
        if (caches == null) {
            onComplete(false)
            return
        }
        onProgress()
        caches.open(CACHE_NAME).then { cache ->
            cache.add(url).then {
                onComplete(true)
            }.`catch` { err ->
                console.error("Download failed:", err)
                onComplete(false)
            }
        }.`catch` { err ->
            console.error("Cache open failed:", err)
            onComplete(false)
        }
    }

    fun deleteTrack(url: String, onComplete: (Boolean) -> Unit) {
        val caches = window.asDynamic().caches
        if (caches == null) {
            onComplete(false)
            return
        }
        caches.open(CACHE_NAME).then { cache ->
            cache.delete(url).then { success ->
                onComplete(success as Boolean)
            }
        }.`catch` {
            onComplete(false)
        }
    }
}

class MusicPlayerApp {
    private val defaultTracks = listOf(
        Track("1", "Ambient Waves", "Helix Instrumental", "SoundHelix Vol. 1", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", "6:12"),
        Track("2", "Cybernetic Breeze", "Synth Lounge", "SoundHelix Vol. 2", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", "7:05"),
        Track("3", "Urban Pulse", "Lofi Beats", "SoundHelix Vol. 3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", "5:44"),
        Track("4", "Solar Wind", "Space Odyssey", "SoundHelix Vol. 4", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", "5:02"),
        Track("5", "Neon Sunset", "Retro Synth", "SoundHelix Vol. 5", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3", "6:03")
    )

    private val downloadedTracks = mutableListOf<Track>()
    private val onlineSearchResults = mutableListOf<Track>()

    private fun getTracks(): List<Track> {
        return (defaultTracks + downloadedTracks + onlineSearchResults).distinctBy { it.id }
    }

    private var currentView = View.EXPLORE
    private var currentTrack: Track? = null
    private var isPlaying = false
    private var isMuted = false
    private var volume = 0.8
    private var isShuffle = false
    private var repeatMode = RepeatMode.OFF
    private var searchQuery = ""
    
    // Set to keep track of downloaded track IDs
    private val downloadedTrackIds = mutableSetOf<String>()
    private val downloadingTrackIds = mutableSetOf<String>()

    private val aggregator = SearchAggregator(listOf(ITunesSearchProvider(), DeezerSearchProvider(), LofiCCProvider()))

    private var isSearchingOnline = false
    private var searchDebounceTimeout: Int? = null

    // Search filters state
    private var selectedSourceFilter = "Todas as Origens"
    private var selectedTypeFilter = "Tudo"
    private var selectedPlaylist: Playlist? = null
    private var activePlaylistQueue: List<Track>? = null

    private fun getPlaybackQueue(): List<Track> {
        return activePlaylistQueue ?: getTracks()
    }

    // Audio Element
    private val audio = document.createElement("audio") as HTMLAudioElement

    // Web Audio visualizer nodes
    private var audioCtx: dynamic = null
    private var analyser: dynamic = null
    private var visualizerAnimationId: Int? = null

    // DOM Elements cache
    private var mainContainer: HTMLElement? = null
    private var playerBar: HTMLElement? = null

    init {
        // Initial setup
        checkDownloads()
        setupAudioListeners()
        setupNetworkListeners()
        registerServiceWorker()
        CloudSyncManager.addListener {
            renderHeaderStatus()
        }
    }

    private fun checkDownloads() {
        downloadedTracks.clear()
        downloadedTracks.addAll(LocalStorageManager.getDownloadedTracks())

        getTracks().forEach { track ->
            OfflineManager.isDownloaded(track.url) { downloaded ->
                if (downloaded) {
                    downloadedTrackIds.add(track.id)
                } else {
                    downloadedTrackIds.remove(track.id)
                }
                render()
            }
        }
    }

    private fun registerServiceWorker() {
        val nav = window.navigator.asDynamic()
        if (nav.serviceWorker != null) {
            nav.serviceWorker.register("sw.js").then { reg ->
                console.log("Service Worker registered with scope: ", reg.scope)
            }.`catch` { err ->
                console.error("Service Worker registration failed: ", err)
            }
        }
    }

    private fun setupAudioListeners() {
        audio.addEventListener("timeupdate", {
            updatePlaybackProgress()
        })

        audio.addEventListener("loadedmetadata", {
            updateDurationDisplay()
        })

        audio.addEventListener("ended", {
            handleTrackEnded()
        })
    }

    private fun setupNetworkListeners() {
        window.addEventListener("online", { renderHeaderStatus() })
        window.addEventListener("offline", { renderHeaderStatus() })
    }

    fun start() {
        renderLayout()
        render()
    }

    // --- Audio Control Methods ---

    private fun togglePlay() {
        if (currentTrack == null && getTracks().isNotEmpty()) {
            loadTrack(getTracks()[0], autoPlay = true)
            return
        }

        if (isPlaying) {
            audio.pause()
            isPlaying = false
        } else {
            // Chrome/Firefox require user gesture to resume audio context
            resumeAudioContext()
            audio.play().then {
                isPlaying = true
                render()
            }.`catch` { err ->
                console.error("Audio play failed:", err)
            }
        }
        render()
    }

    private fun loadTrack(track: Track, autoPlay: Boolean) {
        val wasPlaying = isPlaying || autoPlay
        currentTrack = track
        
        // Check if track is downloaded and read from Cache if offline/available
        OfflineManager.isDownloaded(track.url) { downloaded ->
            if (downloaded) {
                // Fetch from Cache to play offline
                val caches = window.asDynamic().caches
                caches.open(OfflineManager.CACHE_NAME).then { cache ->
                    cache.match(track.url).then { response ->
                        if (response != null) {
                            response.blob().then { blob ->
                                val blobUrl = window.asDynamic().URL.createObjectURL(blob)
                                audio.src = blobUrl
                                if (wasPlaying) {
                                    resumeAudioContext()
                                    audio.play().then {
                                        isPlaying = true
                                        render()
                                    }
                                } else {
                                    isPlaying = false
                                    render()
                                }
                            }
                        } else {
                            playRemoteTrack(track, wasPlaying)
                        }
                    }
                }
            } else {
                playRemoteTrack(track, wasPlaying)
            }
        }
    }

    private fun playRemoteTrack(track: Track, wasPlaying: Boolean) {
        audio.src = track.url
        if (wasPlaying) {
            resumeAudioContext()
            audio.play().then {
                isPlaying = true
                render()
            }.`catch` { err ->
                console.error("Playback failed for remote track:", err)
            }
        } else {
            isPlaying = false
            render()
        }
    }

    private fun nextTrack() {
        val currentTracks = getPlaybackQueue()
        if (currentTracks.isEmpty()) return
        
        val nextIndex = if (isShuffle) {
            (0 until currentTracks.size).random()
        } else {
            val currentIndex = currentTracks.indexOfFirst { it.id == currentTrack?.id }
            if (currentIndex == -1 || currentIndex == currentTracks.size - 1) 0 else currentIndex + 1
        }
        loadTrack(currentTracks[nextIndex], autoPlay = true)
    }

    private fun prevTrack() {
        val currentTracks = getPlaybackQueue()
        if (currentTracks.isEmpty()) return
        
        val prevIndex = if (isShuffle) {
            (0 until currentTracks.size).random()
        } else {
            val currentIndex = currentTracks.indexOfFirst { it.id == currentTrack?.id }
            if (currentIndex == -1 || currentIndex == 0) currentTracks.size - 1 else currentIndex - 1
        }
        loadTrack(currentTracks[prevIndex], autoPlay = true)
    }

    private fun handleTrackEnded() {
        when (repeatMode) {
            RepeatMode.ONE -> {
                audio.currentTime = 0.0
                audio.play()
            }
            RepeatMode.ALL -> nextTrack()
            RepeatMode.OFF -> {
                val currentTracks = getPlaybackQueue()
                val currentIndex = currentTracks.indexOfFirst { it.id == currentTrack?.id }
                if (currentIndex != -1 && currentIndex < currentTracks.size - 1) {
                    nextTrack()
                } else {
                    isPlaying = false
                    render()
                }
            }
        }
    }

    private fun seekTo(percent: Double) {
        if (audio.duration.isNaN() || audio.duration == 0.0) return
        audio.currentTime = audio.duration * percent
    }

    private fun setVolume(vol: Double) {
        volume = vol
        audio.volume = vol
        audio.muted = vol == 0.0 || isMuted
        render()
    }

    private fun toggleMute() {
        isMuted = !isMuted
        audio.muted = isMuted
        render()
    }

    // --- Web Audio Visualizer ---

    private fun resumeAudioContext() {
        if (audioCtx == null) {
            try {
                audioCtx = js("new (window.AudioContext || window.webkitAudioContext)()")
                analyser = audioCtx.createAnalyser()
                analyser.fftSize = 256
                val source = audioCtx.createMediaElementSource(audio)
                source.connect(analyser)
                analyser.connect(audioCtx.destination)
            } catch (e: Exception) {
                console.error("Web Audio Context initialization failed:", e)
            }
        }
        if (audioCtx != null && audioCtx.state == "suspended") {
            audioCtx.resume()
        }
    }

    private fun startVisualizerAnimation(canvas: HTMLCanvasElement) {
        val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
        val bufferLength = analyser.frequencyBinCount as Int
        val dataArray = Uint8Array(bufferLength)

        fun draw() {
            if (currentView != View.VISUALIZER) return
            visualizerAnimationId = window.requestAnimationFrame { draw() }

            analyser.getByteFrequencyData(dataArray)

            val width = canvas.width.toDouble()
            val height = canvas.height.toDouble()

            ctx.clearRect(0.0, 0.0, width, height)

            // Dynamic Gradient Background
            val bgGradient = ctx.createRadialGradient(width / 2, height / 2, 50.0, width / 2, height / 2, width / 2)
            bgGradient.addColorStop(0.0, "#0c0d14")
            bgGradient.addColorStop(1.0, "#06070a")
            ctx.fillStyle = bgGradient
            ctx.fillRect(0.0, 0.0, width, height)

            // Drawing circular frequency bars
            val centerX = width / 2
            val centerY = height / 2
            val radius = 110.0
            val barCount = bufferLength / 2

            // Rotate visualizer slowly
            val time = kotlin.js.Date.now() / 15000.0
            
            for (i in 0 until barCount) {
                val value = dataArray[i].toDouble()
                val percent = value / 255.0
                val barHeight = percent * 80.0

                val angle = (i.toDouble() / barCount.toDouble()) * (kotlin.math.PI * 2) + time
                
                val x1 = centerX + kotlin.math.cos(angle) * radius
                val y1 = centerY + kotlin.math.sin(angle) * radius
                val x2 = centerX + kotlin.math.cos(angle) * (radius + barHeight)
                val y2 = centerY + kotlin.math.sin(angle) * (radius + barHeight)

                // Neon styling
                ctx.beginPath()
                ctx.moveTo(x1, y1)
                ctx.lineTo(x2, y2)
                ctx.lineWidth = 4.0
                ctx.asDynamic().lineCap = "round"
                
                // Color transition from Violet to Cyan
                val gradient = ctx.createLinearGradient(x1, y1, x2, y2)
                gradient.addColorStop(0.0, "hsla(263, 85%, 65%, 0.9)")
                gradient.addColorStop(1.0, "hsla(186, 95%, 48%, 0.9)")
                
                ctx.strokeStyle = gradient
                ctx.shadowBlur = 10.0
                ctx.shadowColor = "rgba(139, 92, 246, 0.4)"
                ctx.stroke()
            }
            ctx.shadowBlur = 0.0 // reset
        }

        draw()
    }

    // --- DOM Construction and Rendering ---

    private fun renderLayout() {
        val root = document.getElementById("root") ?: return
        root.innerHTML = ""

        // 1. Sidebar
        val sidebar = document.createElement("div") as HTMLElement
        sidebar.className = "sidebar"
        sidebar.innerHTML = """
            <div class="brand">
                <i class="fa-solid fa-compact-disc brand-icon"></i>
                <span class="brand-name">Sonix</span>
            </div>
            <div class="menu-section">
                <span class="menu-title">Discover</span>
                <div class="menu-item active" id="nav-explore">
                    <i class="fa-solid fa-compass"></i>
                    <span>Explore</span>
                </div>
                <div class="menu-item" id="nav-downloads">
                    <i class="fa-solid fa-circle-down"></i>
                    <span>Downloads</span>
                </div>
                <div class="menu-item" id="nav-playlists">
                    <i class="fa-solid fa-list-music"></i>
                    <span>Playlists</span>
                </div>
                <div class="menu-item" id="nav-visualizer">
                    <i class="fa-solid fa-wave-square"></i>
                    <span>Visualizer</span>
                </div>
            </div>
        """.trimIndent()

        root.appendChild(sidebar)

        // Sidebar navigation clicks
        sidebar.querySelector("#nav-explore")?.addEventListener("click", {
            switchView(View.EXPLORE)
        })
        sidebar.querySelector("#nav-downloads")?.addEventListener("click", {
            switchView(View.DOWNLOADS)
        })
        sidebar.querySelector("#nav-playlists")?.addEventListener("click", {
            selectedPlaylist = null
            switchView(View.PLAYLISTS)
        })
        sidebar.querySelector("#nav-visualizer")?.addEventListener("click", {
            switchView(View.VISUALIZER)
        })

        // 2. Main Content Wrapper
        val main = document.createElement("div") as HTMLElement
        main.className = "main-content"
        root.appendChild(main)
        mainContainer = main

        // 3. Player Bar
        val player = document.createElement("div") as HTMLElement
        player.className = "player-bar"
        root.appendChild(player)
        playerBar = player
    }

    private fun switchView(view: View) {
        currentView = view
        
        // Remove animation frame if leaving visualizer
        if (view != View.VISUALIZER && visualizerAnimationId != null) {
            window.cancelAnimationFrame(visualizerAnimationId!!)
            visualizerAnimationId = null
        }

        // Update nav active classes
        document.querySelectorAll(".menu-item").asList().forEach { el ->
            el.asDynamic().classList.remove("active")
        }
        val activeNavId = when (view) {
            View.EXPLORE -> "nav-explore"
            View.DOWNLOADS -> "nav-downloads"
            View.PLAYLISTS -> "nav-playlists"
            View.VISUALIZER -> "nav-visualizer"
        }
        (document.getElementById(activeNavId) as? HTMLElement)?.asDynamic()?.classList?.add("active")

        render()
    }

    private fun render() {
        renderHeaderStatus()
        renderMainContent()
        renderPlayerBar()
    }

    private fun renderHeaderStatus() {
        val main = mainContainer ?: return
        var header = main.querySelector(".header") as? HTMLElement
        if (header == null) {
            header = document.createElement("div") as HTMLElement
            header.className = "header"
            main.insertBefore(header, main.firstChild)
        }

        val isOnline = window.navigator.onLine
        val statusText = if (isOnline) "Online Mode" else "Offline Mode"
        val statusClass = if (isOnline) "online" else "offline"

        val syncStatusClass = if (CloudSyncManager.isSyncing) "syncing" else "synced"
        val syncIcon = if (CloudSyncManager.isSyncing) "fa-arrows-rotate" else "fa-cloud"
        val syncText = if (CloudSyncManager.isSyncing) "Sincronizando..." else "Sincronizado"

        header.innerHTML = """
            <div class="search-box">
                <i class="fa-solid fa-magnifying-glass"></i>
                <input type="text" class="search-input" placeholder="Buscar músicas ou artistas..." value="$searchQuery">
            </div>
            <div style="display: flex; align-items: center; gap: 8px;">
                <div class="status-indicator">
                    <div class="status-dot $statusClass"></div>
                    <span>$statusText</span>
                </div>
                <div class="cloud-sync-indicator $syncStatusClass" title="Sincronização na Nuvem em Tempo Real">
                    <i class="fa-solid $syncIcon"></i>
                    <span>$syncText</span>
                </div>
            </div>
        """.trimIndent()

        // Handle Search Input
        val searchInput = header.querySelector(".search-input") as HTMLInputElement
        searchInput.addEventListener("input", {
            searchQuery = searchInput.value
            
            searchDebounceTimeout?.let { window.clearTimeout(it) }
            if (searchQuery.isBlank()) {
                onlineSearchResults.clear()
                isSearchingOnline = false
                renderTrackList()
            } else {
                searchDebounceTimeout = window.setTimeout({
                    performOnlineSearch(searchQuery)
                }, 300)
                renderTrackList()
            }
        })
    }

    private fun performOnlineSearch(query: String) {
        if (query.isBlank() || !window.navigator.onLine) {
            onlineSearchResults.clear()
            renderTrackList()
            return
        }
        isSearchingOnline = true
        renderTrackList()

        aggregator.search(query, selectedSourceFilter).then { results ->
            onlineSearchResults.clear()
            onlineSearchResults.addAll(results)
            isSearchingOnline = false
            
            var pendingCount = onlineSearchResults.size
            if (pendingCount == 0) {
                renderTrackList()
            } else {
                onlineSearchResults.forEach { t ->
                    OfflineManager.isDownloaded(t.url) { downloaded ->
                        if (downloaded) {
                            downloadedTrackIds.add(t.id)
                        } else {
                            downloadedTrackIds.remove(t.id)
                        }
                        pendingCount--
                        if (pendingCount == 0) {
                            renderTrackList()
                        }
                    }
                }
            }
        }.`catch` { err ->
            console.error("Aggregation search failed:", err)
            isSearchingOnline = false
            renderTrackList()
        }
    }

    private fun renderMainContent() {
        val main = mainContainer ?: return
        
        // Check if there is already a view container, else create one
        var viewContainer = main.querySelector(".view-container") as? HTMLElement
        if (viewContainer == null) {
            viewContainer = document.createElement("div") as HTMLElement
            viewContainer.className = "view-container"
            main.appendChild(viewContainer)
        }

        viewContainer.innerHTML = ""

        when (currentView) {
            View.EXPLORE -> {
                // Banner Hero
                val hero = document.createElement("div") as HTMLElement
                hero.className = "hero"
                hero.innerHTML = """
                    <div class="hero-text">
                        <span class="hero-tag">AD-FREE & OPEN SOURCE</span>
                        <h1 class="hero-title">Experience Pure Music Offline</h1>
                        <p class="hero-desc">Download your favorite instrumental and lofi tracks directly to your browser sandbox for uninterrupted offline listening.</p>
                    </div>
                """.trimIndent()
                viewContainer.appendChild(hero)

                // Advanced filters
                renderFilterChips(viewContainer)

                // Tracks list title
                val title = document.createElement("h2") as HTMLElement
                title.className = "section-title"
                title.innerText = "Discover Tracks"
                viewContainer.appendChild(title)

                // Tracklist container
                val trackListContainer = document.createElement("div") as HTMLElement
                trackListContainer.className = "tracks-grid"
                viewContainer.appendChild(trackListContainer)
                renderTrackList()
            }
            View.DOWNLOADS -> {
                // Advanced filters (only type filters make sense for offline downloads)
                renderFilterChips(viewContainer, renderSourceFilter = false)

                val title = document.createElement("h2") as HTMLElement
                title.className = "section-title"
                title.innerText = "Downloaded Tracks"
                viewContainer.appendChild(title)

                val trackListContainer = document.createElement("div") as HTMLElement
                trackListContainer.className = "tracks-grid"
                viewContainer.appendChild(trackListContainer)
                renderTrackList()
            }
            View.PLAYLISTS -> {
                val playlist = selectedPlaylist
                if (playlist == null) {
                    // Render list of playlists
                    val titleContainer = document.createElement("div") as HTMLElement
                    titleContainer.style.display = "flex"
                    titleContainer.style.justifyContent = "space-between"
                    titleContainer.style.alignItems = "center"
                    titleContainer.style.marginBottom = "20px"
                    
                    titleContainer.innerHTML = """
                        <h2 class="section-title" style="margin: 0;">Minhas Playlists</h2>
                        <button class="playlist-play-btn create-playlist-btn">
                            <i class="fa-solid fa-plus"></i> Nova Playlist
                        </button>
                    """.trimIndent()
                    viewContainer.appendChild(titleContainer)
                    
                    titleContainer.querySelector(".create-playlist-btn")?.addEventListener("click", {
                        openCreatePlaylistModal()
                    })
                    
                    val playlistsGrid = document.createElement("div") as HTMLElement
                    playlistsGrid.className = "playlists-grid"
                    viewContainer.appendChild(playlistsGrid)
                    
                    val playlists = PlaylistManager.getPlaylists()
                    if (playlists.isEmpty()) {
                        val empty = document.createElement("div") as HTMLElement
                        empty.className = "empty-state"
                        empty.innerHTML = """
                            <i class="fa-solid fa-list-music"></i>
                            <h3>Nenhuma playlist criada</h3>
                            <p>Crie uma playlist personalizada e adicione suas músicas favoritas!</p>
                        """.trimIndent()
                        playlistsGrid.appendChild(empty)
                    } else {
                        playlists.forEach { p ->
                            val card = document.createElement("div") as HTMLElement
                            card.className = "playlist-card"
                            val initial = if (p.name.isNotEmpty()) p.name.take(1).uppercase() else "🎵"
                            card.innerHTML = """
                                <button class="playlist-card-delete-btn" title="Excluir Playlist">
                                    <i class="fa-solid fa-trash"></i>
                                </button>
                                <div class="playlist-card-art">
                                    $initial
                                    <div class="playlist-card-play-overlay">
                                        <i class="fa-solid fa-circle-play"></i>
                                    </div>
                                </div>
                                <span class="playlist-card-title">${p.name}</span>
                                <span class="playlist-card-tracks">${p.trackIds.size} músicas</span>
                            """.trimIndent()
                            
                            card.addEventListener("click", { event ->
                                val target = (event.target as? Element)
                                    ?: (event.currentTarget as? Element)
                                if (target?.closest(".playlist-card-delete-btn") != null) {
                                    event.stopPropagation()
                                    PlaylistManager.deletePlaylist(p.id)
                                    render()
                                } else {
                                    selectedPlaylist = p
                                    render()
                                }
                            })
                            playlistsGrid.appendChild(card)
                        }
                    }
                } else {
                    // Render custom playlist detail
                    val headerContainer = document.createElement("div") as HTMLElement
                    headerContainer.className = "playlist-header-container"
                    val initial = if (playlist.name.isNotEmpty()) playlist.name.take(1).uppercase() else "🎵"
                    
                    headerContainer.innerHTML = """
                        <div class="playlist-header-art">$initial</div>
                        <div class="playlist-header-info">
                            <span class="playlist-header-tag">PLAYLIST PERSONALIZADA</span>
                            <span class="playlist-header-title">${playlist.name}</span>
                            <span style="color: var(--text-muted); font-size: 0.9rem;">${playlist.trackIds.size} músicas</span>
                            <div class="playlist-header-actions">
                                <button class="playlist-play-btn play-all-btn">
                                    <i class="fa-solid fa-play"></i> Reproduzir
                                </button>
                                <button class="playlist-play-btn rename-playlist-btn">
                                    <i class="fa-solid fa-pen"></i> Renomear
                                </button>
                                <button class="playlist-play-btn delete-playlist-btn">
                                    <i class="fa-solid fa-trash"></i> Excluir
                                </button>
                            </div>
                        </div>
                    """.trimIndent()
                    viewContainer.appendChild(headerContainer)
                    
                    headerContainer.querySelector(".play-all-btn")?.addEventListener("click", {
                        val playlistTracks = getTracks().filter { t -> playlist.trackIds.contains(t.id) }
                        if (playlistTracks.isNotEmpty()) {
                            activePlaylistQueue = playlistTracks
                            loadTrack(playlistTracks[0], autoPlay = true)
                        }
                    })
                    
                    headerContainer.querySelector(".rename-playlist-btn")?.addEventListener("click", {
                        openRenamePlaylistModal(playlist)
                    })
                    
                    headerContainer.querySelector(".delete-playlist-btn")?.addEventListener("click", {
                        PlaylistManager.deletePlaylist(playlist.id)
                        selectedPlaylist = null
                        render()
                    })
                    
                    // Tracks in playlist
                    val playlistTracks = getTracks().filter { t -> playlist.trackIds.contains(t.id) }
                    if (playlistTracks.isEmpty()) {
                        val empty = document.createElement("div") as HTMLElement
                        empty.className = "empty-state"
                        empty.innerHTML = """
                            <i class="fa-solid fa-music"></i>
                            <h3>Nenhuma música nesta playlist</h3>
                            <p>Procure e adicione suas músicas favoritas.</p>
                        """.trimIndent()
                        viewContainer.appendChild(empty)
                    } else {
                        val title = document.createElement("h3") as HTMLElement
                        title.className = "section-title"
                        title.innerText = "Músicas"
                        viewContainer.appendChild(title)

                        val trackContainer = document.createElement("div") as HTMLElement
                        trackContainer.className = "tracks-grid"
                        viewContainer.appendChild(trackContainer)
                        
                        playlistTracks.forEach { track ->
                            val card = createTrackCard(track, playlist.id)
                            trackContainer.appendChild(card)
                        }
                    }
                }
            }
            View.VISUALIZER -> {
                val canvas = document.createElement("canvas") as HTMLCanvasElement
                canvas.className = "visualizer-canvas"
                canvas.width = (window.innerWidth - 250).toInt()
                canvas.height = window.innerHeight.toInt()
                viewContainer.appendChild(canvas)
                startVisualizerAnimation(canvas)
            }
        }
    }

    private fun renderPlayerBar() {
        val player = playerBar ?: return
        val track = currentTrack
        val trackTitle = track?.title ?: "Nenhuma música"
        val trackArtist = track?.artist ?: "Desconhecido"
        val isDownloaded = if (track != null) downloadedTrackIds.contains(track.id) else false
        val isDownloading = if (track != null) downloadingTrackIds.contains(track.id) else false

        player.innerHTML = """
            <div class="player-track-info">
                <i class="fa-solid fa-music player-track-icon"></i>
                <div>
                    <div class="player-track-title">$trackTitle</div>
                    <div class="player-track-artist">$trackArtist</div>
                </div>
            </div>
            
            <div class="player-controls">
                <button class="player-btn" id="player-shuffle" title="Modo Aleatório">
                    <i class="fa-solid ${if (isShuffle) "fa-shuffle active" else "fa-shuffle"}"></i>
                </button>
                <button class="player-btn" id="player-prev" title="Anterior">
                    <i class="fa-solid fa-step-backward"></i>
                </button>
                <button class="player-btn player-play-btn" id="player-play" title="${if (isPlaying) "Pausar" else "Reproduzir"}">
                    <i class="fa-solid ${if (isPlaying) "fa-pause" else "fa-play"}"></i>
                </button>
                <button class="player-btn" id="player-next" title="Próxima">
                    <i class="fa-solid fa-step-forward"></i>
                </button>
                <button class="player-btn" id="player-repeat" title="Modo Repetição">
                    <i class="fa-solid ${when (repeatMode) {
                        RepeatMode.OFF -> "fa-repeat"
                        RepeatMode.ONE -> "fa-repeat active"
                        RepeatMode.ALL -> "fa-repeat active"
                    }}"></i>
                </button>
            </div>
            
            <div class="player-progress">
                <span class="player-time" id="player-current-time">0:00</span>
                <div class="player-progress-bar">
                    <div class="player-progress-fill" id="player-progress-fill"></div>
                </div>
                <span class="player-time" id="player-duration">0:00</span>
            </div>
            
            <div class="player-volume-download">
                <div class="player-volume">
                    <button class="player-btn" id="player-mute">
                        <i class="fa-solid ${if (isMuted || volume == 0.0) "fa-volume-xmark" else "fa-volume-high"}"></i>
                    </button>
                    <input type="range" class="player-volume-slider" id="player-volume-slider" min="0" max="100" value="${(volume * 100).toInt()}">
                </div>
                <button class="player-btn download-btn" id="player-download" title="${if (isDownloaded) "Remover Download" else "Baixar"}">
                    <i class="fa-solid ${if (isDownloading) "fa-spinner fa-spin" else if (isDownloaded) "fa-circle-check" else "fa-circle-down"}"></i>
                </button>
            </div>
        """.trimIndent()

        // Attach event listeners
        player.querySelector("#player-shuffle")?.addEventListener("click", {
            isShuffle = !isShuffle
            render()
        })
        player.querySelector("#player-prev")?.addEventListener("click", {
            prevTrack()
        })
        player.querySelector("#player-play")?.addEventListener("click", {
            togglePlay()
        })
        player.querySelector("#player-next")?.addEventListener("click", {
            nextTrack()
        })
        player.querySelector("#player-repeat")?.addEventListener("click", {
            repeatMode = when (repeatMode) {
                RepeatMode.OFF -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.OFF
            }
            render()
        })
        player.querySelector("#player-mute")?.addEventListener("click", {
            toggleMute()
        })
        
        val volumeSlider = player.querySelector("#player-volume-slider") as? HTMLInputElement
        volumeSlider?.addEventListener("input", {
            setVolume(volumeSlider.value.toDouble() / 100.0)
        })

        val progressBar = player.querySelector(".player-progress-bar") as? HTMLElement
        progressBar?.addEventListener("click", { event ->
            val rect = progressBar.getBoundingClientRect()
            val percent = (event.asDynamic().clientX - rect.left) / rect.width
            seekTo(percent)
        })

        val downloadBtn = player.querySelector("#player-download") as? HTMLElement
        downloadBtn?.addEventListener("click", {
            if (track != null) {
                if (isDownloaded) {
                    downloadingTrackIds.remove(track.id)
                    OfflineManager.deleteTrack(track.url) { success ->
                        if (success) {
                            downloadedTrackIds.remove(track.id)
                            LocalStorageManager.removeDownloadedTrack(track.id)
                            render()
                        }
                    }
                } else {
                    downloadingTrackIds.add(track.id)
                    render()
                    OfflineManager.downloadTrack(track.url, 
                        onProgress = { render() },
                        onComplete = { success ->
                            downloadingTrackIds.remove(track.id)
                            if (success) {
                                downloadedTrackIds.add(track.id)
                                LocalStorageManager.saveDownloadedTrack(track)
                            }
                            render()
                        }
                    )
                }
            }
        })
    }

    private fun renderFilterChips(container: HTMLElement, renderSourceFilter: Boolean = true) {
        val chipsContainer = document.createElement("div") as HTMLElement
        chipsContainer.className = "filter-chips"

        if (renderSourceFilter) {
            val sourceLabel = document.createElement("span") as HTMLElement
            sourceLabel.className = "filter-label"
            sourceLabel.innerText = "Origem:"
            chipsContainer.appendChild(sourceLabel)

            val sources = listOf("Todas as Origens", "iTunes", "Deezer", "LofiCC")
            sources.forEach { source ->
                val chip = document.createElement("button") as HTMLElement
                chip.className = "filter-chip ${if (selectedSourceFilter == source) "active" else ""}"
                chip.innerText = source
                chip.addEventListener("click", {
                    selectedSourceFilter = source
                    performOnlineSearch(searchQuery)
                })
                chipsContainer.appendChild(chip)
            }
        }

        val typeLabel = document.createElement("span") as HTMLElement
        typeLabel.className = "filter-label"
        typeLabel.innerText = "Tipo:"
        chipsContainer.appendChild(typeLabel)

        val types = listOf("Tudo", "Favoritos", "Recentes")
        types.forEach { type ->
            val chip = document.createElement("button") as HTMLElement
            chip.className = "filter-chip ${if (selectedTypeFilter == type) "active" else ""}"
            chip.innerText = type
            chip.addEventListener("click", {
                selectedTypeFilter = type
                renderTrackList()
            })
            chipsContainer.appendChild(chip)
        }

        container.appendChild(chipsContainer)
    }

    private fun renderTrackList() {
        val container = (mainContainer?.querySelector(".tracks-grid") as? HTMLElement) ?: return
        container.innerHTML = ""

        val tracks = when (currentView) {
            View.EXPLORE, View.DOWNLOADS -> getTracks()
            View.PLAYLISTS -> {
                val playlist = selectedPlaylist
                if (playlist != null) getTracks().filter { it.id in playlist.trackIds } else getTracks()
            }
            View.VISUALIZER -> getTracks()
        }

        if (tracks.isEmpty()) {
            val empty = document.createElement("div") as HTMLElement
            empty.className = "empty-state"
            empty.innerHTML = """
                <i class="fa-solid fa-music"></i>
                <h3>Nenhuma música encontrada</h3>
                <p>Tente uma busca diferente ou explore as origens disponíveis.</p>
            """.trimIndent()
            container.appendChild(empty)
        } else {
            tracks.forEach { track ->
                val card = createTrackCard(track, selectedPlaylist?.id)
                container.appendChild(card)
            }
        }
    }

    private fun createTrackCard(track: Track, playlistId: String?): HTMLElement {
        val card = document.createElement("div") as HTMLElement
        card.className = "track-card"
        val isDownloaded = downloadedTrackIds.contains(track.id)
        val isDownloading = downloadingTrackIds.contains(track.id)
        
        card.innerHTML = """
            <div class="track-card-header">
                <div class="track-card-art">
                    <i class="fa-solid fa-music"></i>
                    <div class="track-card-play-overlay">
                        <i class="fa-solid fa-circle-play"></i>
                    </div>
                </div>
                <div class="track-card-actions">
                    <button class="track-card-action-btn download-btn" data-track-id="${track.id}" data-track-url="${track.url}" title="${if (isDownloaded) "Remover Download" else "Baixar"}">
                        <i class="fa-solid ${if (isDownloading) "fa-spinner fa-spin" else if (isDownloaded) "fa-circle-check" else "fa-circle-down"}"></i>
                    </button>
                    ${if (playlistId != null) "<button class=\"track-card-action-btn remove-from-playlist-btn\" data-track-id=\"${track.id}\" title=\"Remover da Playlist\"><i class=\"fa-solid fa-trash\"></i></button>" else ""}
                </div>
            </div>
            <div class="track-card-info">
                <div class="track-card-title">${track.title}</div>
                <div class="track-card-artist">${track.artist}</div>
                <div class="track-card-duration">${track.duration}</div>
            </div>
        """.trimIndent()

        // Play track on card click
        card.querySelector(".track-card-play-overlay")?.addEventListener("click", {
            currentTrack = track
            loadTrack(track, autoPlay = true)
        })

        // Download button
        card.querySelector(".download-btn")?.addEventListener("click", { event ->
            event.stopPropagation()
            val target = (event.target as? Element)
                ?: (event.currentTarget as? Element)
            val trackId = target?.getAttribute("data-track-id") ?: return@addEventListener
            val trackUrl = target.getAttribute("data-track-url") ?: return@addEventListener
            
            if (isDownloaded) {
                downloadingTrackIds.remove(trackId)
                OfflineManager.deleteTrack(trackUrl) { success ->
                    if (success) {
                        downloadedTrackIds.remove(trackId)
                        LocalStorageManager.removeDownloadedTrack(trackId)
                        render()
                    }
                }
            } else {
                downloadingTrackIds.add(trackId)
                render()
                OfflineManager.downloadTrack(trackUrl,
                    onProgress = { render() },
                    onComplete = { success ->
                        downloadingTrackIds.remove(trackId)
                        if (success) {
                            downloadedTrackIds.add(trackId)
                            val trackToSave = track.copy()
                            LocalStorageManager.saveDownloadedTrack(trackToSave)
                        }
                        render()
                    }
                )
            }
        })

        // Remove from playlist button
        card.querySelector(".remove-from-playlist-btn")?.addEventListener("click", { event ->
            event.stopPropagation()
            val target = (event.target as? Element)
                ?: (event.currentTarget as? Element)
            val trackId = target?.getAttribute("data-track-id") ?: return@addEventListener
            if (playlistId != null) {
                PlaylistManager.removeTrackFromPlaylist(playlistId, trackId)
                render()
            }
        })

        return card
    }

    private fun openCreatePlaylistModal() {
        val modal = document.createElement("div") as HTMLElement
        modal.className = "modal"
        modal.innerHTML = """
            <div class="modal-content">
                <h2>Nova Playlist</h2>
                <input type="text" class="modal-input" placeholder="Nome da playlist" id="playlist-name-input" autofocus>
                <div class="modal-actions">
                    <button class="modal-btn cancel">Cancelar</button>
                    <button class="modal-btn create">Criar</button>
                </div>
            </div>
        """.trimIndent()
        
        document.body?.appendChild(modal)
        
        val input = modal.querySelector("#playlist-name-input") as? HTMLInputElement
        
        modal.querySelector(".modal-btn.cancel")?.addEventListener("click", {
            modal.remove()
        })
        
        modal.querySelector(".modal-btn.create")?.addEventListener("click", {
            val name = input?.value?.trim() ?: ""
            if (name.isNotEmpty()) {
                val id = "playlist_${kotlin.js.Date.now()}"
                val newPlaylist = Playlist(id, name, emptyList())
                PlaylistManager.savePlaylist(newPlaylist)
                modal.remove()
                render()
            }
        })
        
        input?.addEventListener("keydown", { event ->
            val keyEvent = event as? KeyboardEvent ?: return@addEventListener
            if (keyEvent.key == "Enter") {
                val name = input.value.trim()
                if (name.isNotEmpty()) {
                    val id = "playlist_${kotlin.js.Date.now()}"
                    val newPlaylist = Playlist(id, name, emptyList())
                    PlaylistManager.savePlaylist(newPlaylist)
                    modal.remove()
                    render()
                }
            }
        })
    }

    private fun openRenamePlaylistModal(playlist: Playlist) {
        val modal = document.createElement("div") as HTMLElement
        modal.className = "modal"
        modal.innerHTML = """
            <div class="modal-content">
                <h2>Renomear Playlist</h2>
                <input type="text" class="modal-input" placeholder="Novo nome da playlist" id="playlist-rename-input" value="${playlist.name}" autofocus>
                <div class="modal-actions">
                    <button class="modal-btn cancel">Cancelar</button>
                    <button class="modal-btn rename">Renomear</button>
                </div>
            </div>
        """.trimIndent()
        
        document.body?.appendChild(modal)
        
        val input = modal.querySelector("#playlist-rename-input") as? HTMLInputElement
        
        modal.querySelector(".modal-btn.cancel")?.addEventListener("click", {
            modal.remove()
        })
        
        modal.querySelector(".modal-btn.rename")?.addEventListener("click", {
            val name = input?.value?.trim() ?: ""
            if (name.isNotEmpty()) {
                val updated = playlist.copy(name = name)
                PlaylistManager.savePlaylist(updated)
                modal.remove()
                render()
            }
        })
        
        input?.addEventListener("keydown", { event ->
            val keyEvent = event as? KeyboardEvent ?: return@addEventListener
            if (keyEvent.key == "Enter") {
                val name = input.value.trim()
                if (name.isNotEmpty()) {
                    val updated = playlist.copy(name = name)
                    PlaylistManager.savePlaylist(updated)
                    modal.remove()
                    render()
                }
            }
        })
    }

    private fun updatePlaybackProgress() {
        val progress = if (audio.duration.isNaN() || audio.duration == 0.0) 0.0 else audio.currentTime / audio.duration
        val progressFill = playerBar?.querySelector("#player-progress-fill") as? HTMLElement
        progressFill?.style?.width = "${(progress * 100).toInt()}%"
        
        updateTimeDisplay()
    }

    private fun updateDurationDisplay() {
        val duration = if (audio.duration.isNaN()) 0 else audio.duration.toInt()
        val minutes = duration / 60
        val seconds = duration % 60
        val secondsStr = if (seconds < 10) "0$seconds" else "$seconds"
        val durationEl = playerBar?.querySelector("#player-duration") as? HTMLElement
        durationEl?.innerText = "$minutes:$secondsStr"
    }

    private fun updateTimeDisplay() {
        val current = audio.currentTime.toInt()
        val minutes = current / 60
        val seconds = current % 60
        val secondsStr = if (seconds < 10) "0$seconds" else "$seconds"
        val currentEl = playerBar?.querySelector("#player-current-time") as? HTMLElement
        currentEl?.innerText = "$minutes:$secondsStr"
    }
}

fun main() {
    val app = MusicPlayerApp()
    app.start()
}
