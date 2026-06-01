package com.vibeflow.player.data

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val url: String,
    val duration: String,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val localPath: String? = null
)
