import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
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

enum class View {
    EXPLORE, DOWNLOADS, VISUALIZER
}

enum class RepeatMode {
    OFF, ONE, ALL
}

object OfflineManager {
    const val CACHE_NAME = "vibeflow-tracks-v1"

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
    private val tracks = listOf(
        Track("1", "Ambient Waves", "Helix Instrumental", "SoundHelix Vol. 1", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", "6:12"),
        Track("2", "Cybernetic Breeze", "Synth Lounge", "SoundHelix Vol. 2", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", "7:05"),
        Track("3", "Urban Pulse", "Lofi Beats", "SoundHelix Vol. 3", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", "5:44"),
        Track("4", "Solar Wind", "Space Odyssey", "SoundHelix Vol. 4", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", "5:02"),
        Track("5", "Neon Sunset", "Retro Synth", "SoundHelix Vol. 5", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3", "6:03")
    )

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
    }

    private fun checkDownloads() {
        tracks.forEach { track ->
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
        if (currentTrack == null && tracks.isNotEmpty()) {
            loadTrack(tracks[0], autoPlay = true)
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
        if (tracks.isEmpty()) return
        
        val nextIndex = if (isShuffle) {
            (0 until tracks.size).random()
        } else {
            val currentIndex = tracks.indexOfFirst { it.id == currentTrack?.id }
            if (currentIndex == -1 || currentIndex == tracks.size - 1) 0 else currentIndex + 1
        }
        loadTrack(tracks[nextIndex], autoPlay = true)
    }

    private fun prevTrack() {
        if (tracks.isEmpty()) return
        
        val prevIndex = if (isShuffle) {
            (0 until tracks.size).random()
        } else {
            val currentIndex = tracks.indexOfFirst { it.id == currentTrack?.id }
            if (currentIndex == -1 || currentIndex == 0) tracks.size - 1 else currentIndex - 1
        }
        loadTrack(tracks[prevIndex], autoPlay = true)
    }

    private fun handleTrackEnded() {
        when (repeatMode) {
            RepeatMode.ONE -> {
                audio.currentTime = 0.0
                audio.play()
            }
            RepeatMode.ALL -> nextTrack()
            RepeatMode.OFF -> {
                val currentIndex = tracks.indexOfFirst { it.id == currentTrack?.id }
                if (currentIndex != -1 && currentIndex < tracks.size - 1) {
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
                <span class="brand-name">VibeFlow</span>
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
            View.VISUALIZER -> "nav-visualizer"
        }
        document.getElementById(activeNavId)?.classList?.add("active")

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

        header.innerHTML = """
            <div class="search-box">
                <i class="fa-solid fa-magnifying-glass"></i>
                <input type="text" class="search-input" placeholder="Search tracks or artists..." value="$searchQuery">
            </div>
            <div class="status-indicator">
                <div class="status-dot $statusClass"></div>
                <span>$statusText</span>
            </div>
        """.trimIndent()

        // Handle Search Input
        val searchInput = header.querySelector(".search-input") as HTMLInputElement
        searchInput.addEventListener("input", {
            searchQuery = searchInput.value
            renderTrackList()
        })
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
                val title = document.createElement("h2") as HTMLElement
                title.className = "section-title"
                title.innerText = "Downloaded Tracks"
                viewContainer.appendChild(title)

                val trackListContainer = document.createElement("div") as HTMLElement
                trackListContainer.className = "tracks-grid"
                viewContainer.appendChild(trackListContainer)
                renderTrackList()
            }
            View.VISUALIZER -> {
                renderVisualizer(viewContainer)
            }
        }
    }

    private fun renderTrackList() {
        val container = mainContainer?.querySelector(".tracks-grid") as? HTMLElement ?: return
        container.innerHTML = ""

        val isOfflineView = currentView == View.DOWNLOADS
        
        val filteredTracks = tracks.filter { track ->
            val matchesSearch = track.title.lowercase().contains(searchQuery.lowercase()) ||
                                track.artist.lowercase().contains(searchQuery.lowercase())
            val matchesView = if (isOfflineView) downloadedTrackIds.contains(track.id) else true
            matchesSearch && matchesView
        }

        if (filteredTracks.isEmpty()) {
            val empty = document.createElement("div") as HTMLElement
            empty.className = "empty-state"
            if (isOfflineView) {
                empty.innerHTML = """
                    <i class="fa-solid fa-circle-down"></i>
                    <h3>No downloaded tracks yet</h3>
                    <p>Go to the Explore tab and click the download button on any track to save it offline.</p>
                """.trimIndent()
            } else {
                empty.innerHTML = """
                    <i class="fa-solid fa-music"></i>
                    <h3>No results found</h3>
                    <p>Try searching for a different keyword or check spelling.</p>
                """.trimIndent()
            }
            container.appendChild(empty)
            return
        }

        filteredTracks.forEach { track ->
            val row = document.createElement("div") as HTMLElement
            val isActive = currentTrack?.id == track.id
            val isPlayingRow = isActive && isPlaying
            
            row.className = "track-row" + (if (isActive) " active" else "")
            
            val isDownloaded = downloadedTrackIds.contains(track.id)
            val isDownloading = downloadingTrackIds.contains(track.id)

            // Setup icons based on state
            val playIcon = if (isPlayingRow) "fa-pause" else "fa-play"
            val downloadIconClass = when {
                isDownloading -> "fa-spinner downloading"
                isDownloaded -> "fa-circle-check downloaded"
                else -> "fa-arrow-down"
            }

            row.innerHTML = """
                <button class="track-play-btn">
                    <i class="fa-solid $playIcon"></i>
                </button>
                <div class="track-info">
                    <div class="track-art">${track.title.take(1)}</div>
                    <div class="track-details">
                        <span class="track-title">${track.title}</span>
                        <span class="track-artist">${track.artist}</span>
                    </div>
                </div>
                <span class="track-album">${track.album}</span>
                <span class="track-duration">${track.duration}</span>
                <div class="track-actions">
                    <button class="action-btn download-btn" title="${if (isDownloaded) "Delete Offline Cache" else "Download Offline"}">
                        <i class="fa-solid $downloadIconClass"></i>
                    </button>
                </div>
            """.trimIndent()

            // Play track on clicking anywhere on the row except action buttons
            row.addEventListener("click", { event ->
                val target = event.target as? HTMLElement
                if (target?.closest(".action-btn") == null) {
                    if (isActive) {
                        togglePlay()
                    } else {
                        loadTrack(track, autoPlay = true)
                    }
                }
            })

            // Download button action
            val dlBtn = row.querySelector(".download-btn") as HTMLElement
            dlBtn.addEventListener("click", { event ->
                event.stopPropagation()
                if (isDownloaded) {
                    // Delete from offline cache
                    OfflineManager.deleteTrack(track.url) { success ->
                        if (success) {
                            downloadedTrackIds.remove(track.id)
                            renderTrackList()
                        }
                    }
                } else if (!isDownloading) {
                    // Add to cache
                    downloadingTrackIds.add(track.id)
                    renderTrackList()
                    OfflineManager.downloadTrack(track.url, 
                        onProgress = {
                            // Already marked in downloadingTrackIds
                        },
                        onComplete = { success ->
                            downloadingTrackIds.remove(track.id)
                            if (success) {
                                downloadedTrackIds.add(track.id)
                            }
                            renderTrackList()
                        }
                    )
                }
            })

            container.appendChild(row)
        }
    }

    private fun renderVisualizer(container: HTMLElement) {
        val wrap = document.createElement("div") as HTMLElement
        wrap.className = "visualizer-container"

        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.className = "visualizer-canvas"
        wrap.appendChild(canvas)

        // Setup resize handling for canvas
        window.addEventListener("resize", {
            if (currentView == View.VISUALIZER) {
                canvas.width = wrap.clientWidth
                canvas.height = wrap.clientHeight
            }
        })

        // Initial canvas dimensions (must set properties explicitly, not just CSS)
        // Wait, wait for elements to render to get bounds. Let's do it after append.
        window.setTimeout({
            canvas.width = wrap.clientWidth
            canvas.height = wrap.clientHeight
            if (analyser != null) {
                startVisualizerAnimation(canvas)
            }
        }, 50)

        // Visualizer Overlay Info
        val title = currentTrack?.title ?: "No Track Selected"
        val artist = currentTrack?.artist ?: "Press Play to start"
        val playingClass = if (isPlaying && currentTrack != null) "playing" else ""
        val initialLetter = currentTrack?.title?.take(1) ?: "🎵"

        val overlay = document.createElement("div") as HTMLElement
        overlay.className = "visualizer-overlay"
        overlay.innerHTML = """
            <div class="visualizer-art $playingClass">$initialLetter</div>
            <div class="visualizer-title">$title</div>
            <div class="visualizer-artist">$artist</div>
        """.trimIndent()

        wrap.appendChild(overlay)
        container.appendChild(wrap)
    }

    private fun renderPlayerBar() {
        val bar = playerBar ?: return
        bar.innerHTML = ""

        val track = currentTrack
        val trackTitle = track?.title ?: "Not Playing"
        val trackArtist = track?.artist ?: "Select a track to start"
        val initialLetter = track?.title?.take(1) ?: "🎵"

        val playIcon = if (isPlaying) "fa-pause" else "fa-play"
        val muteIcon = if (isMuted || volume == 0.0) "fa-volume-xmark" else "fa-volume-high"

        val playingClass = if (isPlaying) "playing" else ""

        val repeatClass = when (repeatMode) {
            RepeatMode.ONE -> "active"
            RepeatMode.ALL -> "active"
            RepeatMode.OFF -> ""
        }
        val repeatIcon = if (repeatMode == RepeatMode.ONE) "fa-repeat-1" else "fa-repeat"
        
        val shuffleClass = if (isShuffle) "active" else ""

        // HTML Layout for player bar
        bar.innerHTML = """
            <div class="player-track-info">
                <div class="player-track-art $playingClass">$initialLetter</div>
                <div class="player-track-details">
                    <span class="player-track-title">$trackTitle</span>
                    <span class="player-track-artist">$trackArtist</span>
                </div>
            </div>
            <div class="player-controls-container">
                <div class="player-buttons">
                    <button class="player-btn $shuffleClass" id="player-shuffle" title="Shuffle">
                        <i class="fa-solid fa-shuffle"></i>
                    </button>
                    <button class="player-btn" id="player-prev" title="Previous">
                        <i class="fa-solid fa-backward-step"></i>
                    </button>
                    <button class="player-btn-main" id="player-play-pause" title="Play/Pause">
                        <i class="fa-solid $playIcon"></i>
                    </button>
                    <button class="player-btn" id="player-next" title="Next">
                        <i class="fa-solid fa-forward-step"></i>
                    </button>
                    <button class="player-btn $repeatClass" id="player-repeat" title="Repeat Mode">
                        <i class="fa-solid $repeatIcon"></i>
                    </button>
                </div>
                <div class="progress-container">
                    <span class="time-label" id="current-time">0:00</span>
                    <div class="progress-slider-wrapper">
                        <input type="range" class="progress-slider" id="progress-slider" min="0" max="100" value="0">
                    </div>
                    <span class="time-label" id="duration-time">${track?.duration ?: "0:00"}</span>
                </div>
            </div>
            <div class="player-right-controls">
                <div class="volume-container">
                    <button class="action-btn" id="player-mute" title="Mute/Unmute">
                        <i class="fa-solid $muteIcon"></i>
                    </button>
                    <input type="range" class="volume-slider" id="volume-slider" min="0" max="100" value="${(volume * 100).toInt()}">
                </div>
                <button class="action-btn" id="player-visualizer-toggle" title="Full Screen Visualizer">
                    <i class="fa-solid fa-expand"></i>
                </button>
            </div>
        """.trimIndent()

        // Event Listeners for Player controls
        bar.querySelector("#player-play-pause")?.addEventListener("click", { togglePlay() })
        bar.querySelector("#player-next")?.addEventListener("click", { nextTrack() })
        bar.querySelector("#player-prev")?.addEventListener("click", { prevTrack() })
        
        bar.querySelector("#player-shuffle")?.addEventListener("click", {
            isShuffle = !isShuffle
            render()
        })
        
        bar.querySelector("#player-repeat")?.addEventListener("click", {
            repeatMode = when (repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            render()
        })

        bar.querySelector("#player-mute")?.addEventListener("click", { toggleMute() })

        val volSlider = bar.querySelector("#volume-slider") as HTMLInputElement
        volSlider.addEventListener("input", {
            setVolume(volSlider.value.toDouble() / 100.0)
        })

        bar.querySelector("#player-visualizer-toggle")?.addEventListener("click", {
            switchView(View.VISUALIZER)
        })

        // Progress slider scrubbing
        val progSlider = bar.querySelector("#progress-slider") as HTMLInputElement
        progSlider.addEventListener("change", {
            seekTo(progSlider.value.toDouble() / 100.0)
        })
    }

    private fun updatePlaybackProgress() {
        if (audio.duration.isNaN() || audio.duration == 0.0) return
        
        val percent = (audio.currentTime / audio.duration) * 100
        val slider = document.getElementById("progress-slider") as? HTMLInputElement
        if (slider != null) {
            slider.value = percent.toInt().toString()
        }

        val currentLabel = document.getElementById("current-time") as? HTMLElement
        if (currentLabel != null) {
            currentLabel.innerText = formatTime(audio.currentTime)
        }
    }

    private fun updateDurationDisplay() {
        val durationLabel = document.getElementById("duration-time") as? HTMLElement
        if (durationLabel != null && !audio.duration.isNaN()) {
            durationLabel.innerText = formatTime(audio.duration)
        }
    }

    private fun formatTime(seconds: Double): String {
        if (seconds.isNaN()) return "0:00"
        val mins = (seconds / 60).toInt()
        val secs = (seconds % 60).toInt()
        return "$mins:${if (secs < 10) "0" else ""}$secs"
    }
}

fun main() {
    window.addEventListener("DOMContentLoaded", {
        val app = MusicPlayerApp()
        app.start()
    })
}
