package com.akshansh.aktv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val rows = remember(vm.channels, vm.favIds, vm.recentIds) {
        Categorizer.buildRows(vm.channels, vm.favIds, vm.recentIds)
    }
    // Hoisted as a State object so only the hero recomposes while you move the cursor.
    val focused = remember { mutableStateOf<Channel?>(null) }
    val onFocus = remember { { c: Channel -> focused.value = c } }

    Column(Modifier.fillMaxSize()) {
        Hero(focused, rows.firstOrNull()?.items?.firstOrNull(), vm.channels.size)
        when {
            rows.isEmpty() && vm.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            rows.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(vm.error ?: "No channels found.", color = TextDim, fontSize = 16.sp)
                    Text(
                        "Retry",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Accent)
                            .clickable { vm.refresh() }
                            .padding(horizontal = 28.dp, vertical = 10.dp),
                    )
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                items(rows, key = { it.title }) { row ->
                    Text(
                        row.title,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 48.dp, top = 6.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(row.items, key = { it.id }) { ch ->
                            ChannelCard(ch, onFocus) {
                                vm.play(row.items, row.items.indexOf(ch))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(state: State<Channel?>, fallback: Channel?, total: Int) {
    val ch = state.value ?: fallback
    Box(Modifier.fillMaxWidth().height(190.dp)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xFF2A1D63), Color(0xFF12101F), Bg))
            )
        )
        Crossfade(
            targetState = ch,
            animationSpec = tween(250),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp),
            label = "hero",
        ) { c ->
            if (c?.logo != null) {
                AsyncImage(
                    model = c.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alpha = 0.22f,
                    modifier = Modifier.height(170.dp).width(300.dp),
                )
            }
        }
        // fade into the rows below
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Bg)
            )
        )
        Row(
            Modifier.padding(start = 48.dp, top = 18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("AKTV", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(" v2", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
            Text("   by Akshansh", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(
            "$total channels",
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 22.dp),
        )
        Crossfade(
            targetState = ch,
            animationSpec = tween(250),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 22.dp),
            label = "heroText",
        ) { c ->
            if (c != null) Column(Modifier.width(520.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "LIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE5334B))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text("  ${c.category}", color = TextDim, fontSize = 12.sp)
                }
                Text(
                    c.name,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Press OK to watch  ·  Menu to refresh",
                    color = TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ChannelCard(ch: Channel, onFocus: (Channel) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(140), label = "scale")
    val shape = RoundedCornerShape(10.dp)
    val ctx = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(ctx).data(ch.logo).size(256).crossfade(true).build()
    )
    val loaded = painter.state is AsyncImagePainter.State.Success

    Box(
        Modifier
            .width(168.dp)
            .height(96.dp)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus(ch)
            }
            .clip(shape)
            .background(CardBg)
            .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp), Alignment.Center) {
                if (!loaded) {
                    Text(
                        ch.name.take(2).uppercase(),
                        color = Accent,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (ch.logo != null) {
                    Image(painter, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            Text(
                ch.name,
                color = Color.White.copy(alpha = if (focused) 1f else 0.75f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
            )
        }
    }
}
