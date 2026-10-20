package dev.matchday.tv.feature.player

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.MetaChip
import dev.matchday.tv.feature.live.PlayableChannel
import org.koin.compose.koinInject
import tv.own.owntv.player.OwnTVPlayer

@Composable
fun PlayerScreen(
    playable: PlayableChannel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player: OwnTVPlayer = koinInject()
    val playing by player.isPlaying.collectAsStateWithLifecycle()
    val buffering by player.buffering.collectAsStateWithLifecycle()
    val error by player.error.collectAsStateWithLifecycle()
    val videoRes by player.videoRes.collectAsStateWithLifecycle()
    val chips by player.streamChips.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    LaunchedEffect(playable.channel.id) {
        player.play(
            url = playable.channel.streamUrl,
            title = playable.channel.name,
            logoUrl = playable.channel.logoUrl,
            isLive = true,
            userAgent = playable.source?.userAgent,
            httpHeaders = playable.channel.httpHeaders,
            drmConfig = playable.channel.drmConfig,
            manifestType = playable.channel.manifestType,
        )
    }
    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MpvVideoSurface(player = player, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xCC000000),
                        0.25f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color(0xCC000000),
                    ),
                )
                .padding(horizontal = 42.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = playable.channel.name,
                        style = LocalMatchdayType.current.title.copy(fontSize = 30.sp),
                        color = MatchdayColors.Ivory,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        playable.categoryName?.let { MetaChip(it) }
                        playable.source?.let { MetaChip(it.name) }
                        videoRes?.let { MetaChip(it) }
                    }
                }
                if (chips.isNotEmpty()) {
                    Text(
                        text = chips.joinToString("  ·  "),
                        style = LocalMatchdayType.current.meta,
                        color = MatchdayColors.Muted,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        error != null -> "Stream failed · ${error.toString()}"
                        buffering -> "Tuning…"
                        playing -> "Playing"
                        else -> "Preparing…"
                    },
                    style = LocalMatchdayType.current.meta,
                    color = if (error != null) MatchdayColors.Brick else MatchdayColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "BACK  ·  Exit player",
                    style = LocalMatchdayType.current.label.copy(fontSize = 11.sp),
                    color = MatchdayColors.Dim,
                )
            }
        }
    }
}

@Composable
private fun MpvVideoSurface(player: OwnTVPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                isOpaque = true
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        surface?.release()
                        surface = Surface(texture).also {
                            player.setSurfaceSize(width, height)
                            player.attachSurface(it)
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        player.setSurfaceSize(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        player.detachSurface()
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}
