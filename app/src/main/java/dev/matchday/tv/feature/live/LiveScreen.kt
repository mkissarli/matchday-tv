package dev.matchday.tv.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.matchday.tv.design.EmptyState
import dev.matchday.tv.design.FocusableSurface
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.MetaChip
import dev.matchday.tv.design.SectionLabel
import dev.matchday.tv.BuildConfig
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun LiveScreen(
    onPlay: (PlayableChannel) -> Unit,
    modifier: Modifier = Modifier,
    vm: LiveViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "Live TV",
                style = LocalMatchdayType.current.display,
                color = MatchdayColors.Ivory,
            )
            Spacer(Modifier.width(18.dp))
            if (state.channels.isNotEmpty()) {
                MetaChip(if (state.channels.size == 1) "1 channel" else "${state.channels.size} channels")
                Spacer(Modifier.width(8.dp))
                MetaChip(if (state.sourceCount == 1) "1 source" else "${state.sourceCount} sources")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your playlists, your channels. Nothing is bundled.",
            style = LocalMatchdayType.current.body,
            color = MatchdayColors.Muted,
        )
        Spacer(Modifier.height(26.dp))

        when {
            state.loading -> {
                Text("Opening the catalogue…", style = LocalMatchdayType.current.body, color = MatchdayColors.Muted)
            }
            state.channels.isEmpty() -> {
                AddPlaylist(state = state, onImport = vm::importPlaylist)
            }
            else -> {
                ChannelList(
                    channels = state.channels,
                    onPlay = onPlay,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AddPlaylist(state: LiveUiState, onImport: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("My playlist") }
    var url by rememberSaveable { mutableStateOf(if (BuildConfig.DEBUG) "http://10.0.2.2:8088/playlist.m3u" else "") }

    Row(
        horizontalArrangement = Arrangement.spacedBy(34.dp),
        verticalAlignment = Alignment.Top,
    ) {
        EmptyState(
            eyebrow = "SOURCE",
            title = "Bring a playlist.",
            body = "Paste an M3U or M3U8 URL. Matchday stores it locally and never ships channels or credentials.",
            modifier = Modifier.weight(1f),
        )
        Column(
            modifier = Modifier
                .width(520.dp)
                .background(MatchdayColors.Surface, RoundedCornerShape(18.dp))
                .border(1.dp, MatchdayColors.CardLine, RoundedCornerShape(18.dp))
                .padding(24.dp),
        ) {
            SectionLabel("Add a source")
            Spacer(Modifier.height(16.dp))
            MatchdayTextField(
                value = name,
                onValueChange = { name = it },
                label = "Playlist name",
            )
            Spacer(Modifier.height(14.dp))
            MatchdayTextField(
                value = url,
                onValueChange = { url = it },
                label = if (BuildConfig.DEBUG) "M3U / M3U8 URL · local test server" else "M3U / M3U8 URL",
                keyboardType = KeyboardType.Uri,
            )
            Spacer(Modifier.height(20.dp))
            FocusableSurface(
                onClick = { onImport(name, url) },
                selected = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(
                    text = if (state.importing) "Importing…" else "Import playlist",
                    style = LocalMatchdayType.current.subtitle,
                    color = MatchdayColors.Ivory,
                )
            }
            if (state.importing) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.progress ?: "Reading the playlist…",
                    style = LocalMatchdayType.current.meta,
                    color = MatchdayColors.Muted,
                )
            }
            state.error?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    style = LocalMatchdayType.current.meta,
                    color = MatchdayColors.Brick,
                )
            }
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<PlayableChannel>,
    onPlay: (PlayableChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(channels.firstOrNull()?.channel?.id) {
        delay(120)
        runCatching { first.requestFocus() }
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        itemsIndexed(channels, key = { _, playable -> playable.channel.id }) { index, playable ->
            ChannelRow(
                playable = playable,
                onClick = { onPlay(playable) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

@Composable
private fun ChannelRow(playable: PlayableChannel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 13.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = playable.channel.number?.toString().orEmpty().padStart(3, ' '),
                style = LocalMatchdayType.current.meta.copy(fontSize = 13.sp),
                color = MatchdayColors.Dim,
                modifier = Modifier.width(46.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = playable.channel.name,
                    style = LocalMatchdayType.current.subtitle.copy(fontSize = 18.sp),
                    color = MatchdayColors.Ivory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!playable.categoryName.isNullOrBlank()) {
                    Text(
                        text = playable.categoryName,
                        style = LocalMatchdayType.current.meta.copy(fontSize = 13.sp),
                        color = MatchdayColors.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            playable.source?.let {
                MetaChip(it.name)
            }
        }
    }
}

@Composable
private fun MatchdayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Column {
        Text(
            text = label.uppercase(),
            style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
            color = if (focused) MatchdayColors.Brick else MatchdayColors.Dim,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MatchdayColors.Ink, RoundedCornerShape(10.dp))
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) MatchdayColors.Focus else MatchdayColors.CardLine,
                    RoundedCornerShape(10.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalMatchdayType.current.body.copy(color = MatchdayColors.Ivory),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isBlank()) {
                        Text(
                            text = if (keyboardType == KeyboardType.Uri) "http://…" else "Playlist",
                            style = LocalMatchdayType.current.body,
                            color = Color(0xFF5F5B55),
                        )
                    }
                    inner()
                },
            )
        }
    }
}
