package com.akshansh.aktv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val cache = File(app.filesDir, "playlist.m3u")

    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var favIds by mutableStateOf(prefs.favorites()); private set
    var recentIds by mutableStateOf(prefs.recents()); private set

    // Player state
    var queue by mutableStateOf<List<Channel>>(emptyList()); private set
    var playIndex by mutableIntStateOf(-1); private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            // 1) show the cached playlist instantly
            if (channels.isEmpty() && cache.exists()) {
                val cached = withContext(Dispatchers.Default) {
                    runCatching { M3uParser.parse(cache.readText()) }.getOrDefault(emptyList())
                }
                if (cached.isNotEmpty()) channels = cached
            }
            // 2) then fetch the fresh one
            try {
                val text = withContext(Dispatchers.IO) {
                    download(BuildConfig.PLAYLIST_URL).also { runCatching { cache.writeText(it) } }
                }
                val parsed = withContext(Dispatchers.Default) { M3uParser.parse(text) }
                if (parsed.isNotEmpty()) channels = parsed else error = "The playlist is empty."
            } catch (e: Exception) {
                if (channels.isEmpty()) error = "Couldn't load the playlist (${e.message ?: "network error"})."
            }
            loading = false
        }
    }

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "AKTV/2.0")
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun play(list: List<Channel>, index: Int) {
        queue = list
        playIndex = index
        prefs.pushRecent(list[index].id)
        recentIds = prefs.recents()
    }

    fun zap(delta: Int) {
        if (queue.isEmpty()) return
        play(queue, (playIndex + delta + queue.size) % queue.size)
    }

    fun stop() { playIndex = -1 }

    fun toggleFavorite(ch: Channel) {
        prefs.toggleFavorite(ch.id)
        favIds = prefs.favorites()
    }
}
