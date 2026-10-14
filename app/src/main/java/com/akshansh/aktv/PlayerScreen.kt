package com.akshansh.aktv

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.net.URLEncoder

private const val DEFAULT_UA =
    "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** One way of trying a stream: direct or through the Cloudflare Worker, forced HLS or auto-detected. */
private data class Plan(val proxy: Boolean, val hls: Boolean)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(vm: HomeViewModel) {
    val ctx = LocalContext.current
    val exo = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { exo.release() } }

    val ch = vm.queue.getOrNull(vm.playIndex) ?: return
    val focusRequester = remember { FocusRequester() }
    var overlay by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(tick, ch.id) {
        overlay = true
        delay(4500)
        overlay = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back || e.key == Key.Escape) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionUp, Key.ChannelUp, Key.PageUp -> vm.zap(1)
                        Key.DirectionDown, Key.ChannelDown, Key.PageDown -> vm.zap(-1)
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                            if (overlay) vm.toggleFavorite(ch) else tick++
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause ->
                            exo.playWhenReady = !exo.playWhenReady
                        else -> tick++
                    }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    keepScreenOn = true
                    isFocusable = false
                    player = exo
                }
            },
        )

        // A fresh session (retry state, spinner) for every channel
        key(ch.id) { Session(exo, ch) }

        AnimatedVisibility(
            visible = overlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            InfoBar(ch, vm.playIndex, vm.queue.size, ch.id in vm.favIds)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun BoxScope.Session(exo: ExoPlayer, ch: Channel) {
    val ctx = LocalContext.current
    val hasProxy = BuildConfig.WORKER_URL.isNotBlank()
    val plans = remember {
        val m3u8 = ch.url.contains(".m3u8", ignoreCase = true)
        buildList {
            add(Plan(proxy = false, hls = m3u8))                 // 1. straight to the stream
            if (!m3u8) add(Plan(proxy = false, hls = true))      // 2. maybe it's HLS without .m3u8
            if (hasProxy) add(Plan(proxy = true, hls = m3u8))    // 3. via your Cloudflare Worker
        }
    }
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var everReady by remember { mutableStateOf(false) }

    fun advance() {
        if (attempt + 1 < plans.size) attempt++ else failed = true
    }

    DisposableEffect(exo) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) everReady = true
            }

            override fun onPlayerError(error: PlaybackException) = advance()
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l) }
    }

    LaunchedEffect(attempt) {
        failed = false
        buffering = true
        exo.setMediaSource(buildSource(ctx, ch, plans[attempt]))
        exo.playWhenReady = true
        exo.prepare()
        delay(30_000)                      // dead stream? move on to the next plan
        if (!everReady) advance()
    }

    if (failed) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This channel isn't responding", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Press ▲ or ▼ to try another channel",
                color = TextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    } else if (buffering) {
        CircularProgressIndicator(Modifier.align(Alignment.Center), color = Accent)
    }
}

@OptIn(UnstableApi::class)
private fun buildSource(ctx: Context, ch: Channel, plan: Plan): MediaSource {
    val url = if (plan.proxy) proxied(ch) else ch.url
    val props = HashMap<String, String>()
    if (!plan.proxy) ch.referrer?.let { props["Referer"] = it }
    val http = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
        .setUserAgent(if (plan.proxy) "AKTV/2.0" else (ch.userAgent ?: DEFAULT_UA))
        .setDefaultRequestProperties(props)
    val item = MediaItem.Builder()
        .setUri(url)
        .apply { if (plan.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .build()
    return DefaultMediaSourceFactory(ctx).setDataSourceFactory(http).createMediaSource(item)
}

/** https://your-worker.workers.dev/?url=<stream>&k=<key>&ua=..&ref=.. */
private fun proxied(ch: Channel): String {
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    val b = StringBuilder(BuildConfig.WORKER_URL.trimEnd('/')).append("/?url=").append(enc(ch.url))
    if (BuildConfig.WORKER_KEY.isNotBlank()) b.append("&k=").append(enc(BuildConfig.WORKER_KEY))
    ch.userAgent?.let { b.append("&ua=").append(enc(it)) }
    ch.referrer?.let { b.append("&ref=").append(enc(it)) }
    return b.toString()
}

@Composable
private fun InfoBar(ch: Channel, index: Int, total: Int, fav: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
            .padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 112.dp, height = 64.dp).clip(RoundedCornerShape(10.dp)).background(CardBg),
                Alignment.Center,
            ) {
                AsyncImage(
                    model = ch.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${ch.category}  ·  ${index + 1} of $total", color = TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("▲ ▼ change channel      OK add / remove favourite      Back exit", color = TextDim, fontSize = 11.sp)
            }
            if (fav) Text("♥ Favourite", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
