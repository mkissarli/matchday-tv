package dev.matchday.tv.feature.player

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.matchday.domain.playback.PlaybackCandidate
import dev.matchday.domain.playback.PlaybackFailureKind
import dev.matchday.domain.playback.PlaybackSupervisor
import dev.matchday.tv.data.PlaybackPreferences
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.MetaChip
import dev.matchday.tv.feature.live.PlayableChannel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.player.OwnTVPlayer

private data class BoundStream(
    val candidate: PlaybackCandidate,
    val playable: PlayableChannel,
    val url: String,
)

/**
 * Player with a small failover policy:
 *  - variants of the current logical channel first (quality fallback),
 *  - then sibling channels for the same fixture (language priority),
 *  - failed streams cool down before they may be retried.
 */
@Composable
fun PlayerScreen(
    queue: List<PlayableChannel>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player: OwnTVPlayer = koinInject()
    val playbackPreferences: PlaybackPreferences = koinInject()
    val playing by player.isPlaying.collectAsStateWithLifecycle()
    val buffering by player.buffering.collectAsStateWithLifecycle()
    val error by player.error.collectAsStateWithLifecycle()
    val videoRes by player.videoRes.collectAsStateWithLifecycle()
    val chips by player.streamChips.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    val bindings = remember(queue) {
        queue.flatMap { playable ->
            val logical = playable.logical
            val variants = logical?.variants.orEmpty()
            if (variants.isEmpty()) {
                listOf(
                    BoundStream(
                        candidate = PlaybackCandidate(
                            key = "channel:${playable.channel.id}",
                            url = playable.channel.streamUrl,
                            channelKey = playable.channel.id.toString(),
                            channelName = playable.channel.name,
                            languageCode = playable.languageCode ?: logical?.language?.code,
                        ),
                        playable = playable,
                        url = playable.channel.streamUrl,
                    ),
                )
            } else {
                variants.map { variant ->
                    BoundStream(
                        candidate = PlaybackCandidate(
                            key = "channel:${playable.channel.id}:${variant.rawChannelId}",
                            url = variant.streamUrl,
                            channelKey = playable.channel.id.toString(),
                            channelName = playable.channel.name,
                            quality = variant.quality,
                            languageCode = playable.languageCode ?: logical?.language?.code,
                        ),
                        playable = playable,
                        url = variant.streamUrl,
                    )
                }
            }
        }.distinctBy { it.candidate.key }
    }

    val supervisor = remember(queue) { PlaybackSupervisor() }
    val maxTier = playbackPreferences.maxTier()
    var activeKey by remember(queue) { mutableStateOf<String?>(null) }
    var lastHandledError by remember(queue) { mutableStateOf<Any?>(null) }
    var switchNotice by remember(queue) { mutableStateOf<String?>(null) }
    var exhausted by remember(queue) { mutableStateOf(false) }
    var paused by remember(queue) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    LaunchedEffect(queue) {
        val first = supervisor.start(bindings.map { it.candidate }, maxTier)?.candidate
        activeKey = first?.key ?: bindings.firstOrNull()?.candidate?.key
    }

    val active = bindings.firstOrNull { it.candidate.key == activeKey } ?: bindings.firstOrNull()

    LaunchedEffect(active?.candidate?.key) {
        val stream = active ?: return@LaunchedEffect
        exhausted = false
        paused = false
        player.play(
            url = stream.url,
            title = stream.playable.channel.name,
            logoUrl = stream.playable.channel.logoUrl,
            isLive = true,
            userAgent = stream.playable.source?.userAgent,
            httpHeaders = stream.playable.channel.httpHeaders,
            drmConfig = stream.playable.channel.drmConfig,
            manifestType = stream.playable.channel.manifestType,
        )
    }

    LaunchedEffect(error) {
        val failure = error ?: return@LaunchedEffect
        if (failure == lastHandledError) return@LaunchedEffect
        lastHandledError = failure
        val next = supervisor.onFailure(failureKind(failure), maxTier)
        if (next != null) {
            activeKey = next.candidate.key
            switchNotice = "Switched to ${next.candidate.quality.label.ifBlank { next.candidate.channelName }}"
        } else {
            exhausted = true
            switchNotice = "No working stream. Retrying shortly…"
            delay(20_000)
            val retry = supervisor.retry(maxTier)
            if (retry != null) {
                activeKey = retry.candidate.key
                exhausted = false
                switchNotice = "Retrying ${retry.candidate.channelName}"
            }
        }
    }

    LaunchedEffect(playing) {
        if (playing) {
            delay(15_000)
            supervisor.onPlaying()
        }
    }

    LaunchedEffect(switchNotice) {
        if (switchNotice != null) {
            delay(4_000)
            switchNotice = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.stop() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter ||
                        event.key == Key.Spacebar || event.key == Key.MediaPlayPause)
                ) {
                    player.togglePlayPause()
                    paused = !paused
                    true
                } else {
                    false
                }
            },
    ) {
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
                        text = active?.playable?.channel?.name ?: "Player",
                        style = LocalMatchdayType.current.title.copy(fontSize = 30.sp),
                        color = MatchdayColors.Ivory,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        active?.playable?.categoryName?.let { MetaChip(it) }
                        active?.playable?.source?.let { MetaChip(it.name) }
                        active?.candidate?.quality?.label?.takeIf { it.isNotBlank() }?.let { MetaChip(it) }
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
                        exhausted -> "No working stream · retrying"
                        error != null -> "Stream failed · ${error.toString()}"
                        buffering -> "Tuning…"
                        paused -> "Paused"
                        playing -> "Playing"
                        else -> "Preparing…"
                    },
                    style = LocalMatchdayType.current.meta,
                    color = if (error != null || exhausted) MatchdayColors.Brick else MatchdayColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = switchNotice ?: if (paused) "ENTER  ·  Resume    BACK  ·  Exit" else "ENTER  ·  Pause    BACK  ·  Exit",
                    style = LocalMatchdayType.current.label.copy(fontSize = 11.sp),
                    color = if (switchNotice != null) MatchdayColors.Pitch else MatchdayColors.Dim,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun failureKind(error: Any): PlaybackFailureKind {
    val text = error.toString().lowercase()
    return when {
        "http" in text || "404" in text || "403" in text -> PlaybackFailureKind.HTTP
        "decode" in text || "codec" in text -> PlaybackFailureKind.DECODE
        "timeout" in text || "stall" in text || "buffer" in text -> PlaybackFailureKind.STALL
        "network" in text || "connect" in text || "unreachable" in text -> PlaybackFailureKind.NETWORK
        else -> PlaybackFailureKind.UNKNOWN
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
