package dev.matchday.tv.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.matchday.tv.BuildConfig
import dev.matchday.tv.design.EmptyState
import dev.matchday.tv.design.FocusableSurface
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.MediaPreview
import dev.matchday.tv.design.MetaChip
import dev.matchday.tv.design.RemoteBadge
import dev.matchday.tv.design.SectionLabel
import dev.matchday.tv.design.activationClick
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun LiveScreen(
    onPlay: (List<PlayableChannel>) -> Unit,
    modifier: Modifier = Modifier,
    vm: LiveViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<PlayableChannel?>(null) }

    val visible = remember(state.channels, state.favoriteRawIds, state.recentRawIds, category, query) {
        state.channels.filter { channel ->
            matchesCategory(channel, category, state.favoriteRawIds, state.recentRawIds) &&
                (query.isBlank() || channel.channel.name.contains(query, ignoreCase = true))
        }
    }
    LaunchedEffect(visible) {
        if (visible.none { it.channel.id == selected?.channel?.id }) selected = visible.firstOrNull()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Live TV",
                style = LocalMatchdayType.current.title.copy(fontSize = 30.sp),
                color = MatchdayColors.Ivory,
            )
            Spacer(Modifier.width(12.dp))
            if (state.logicalCount > 0) {
                Text(
                    text = if (state.logicalCount == 1) "(1 channel)" else "(${state.logicalCount} channels)",
                    style = LocalMatchdayType.current.title.copy(fontSize = 24.sp),
                    color = MatchdayColors.Muted,
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.rawCount > 0 && state.rawCount != state.logicalCount) {
                Text(
                    text = "${state.rawCount} raw → ${state.logicalCount} logical",
                    style = LocalMatchdayType.current.meta,
                    color = MatchdayColors.Dim,
                )
                Spacer(Modifier.width(12.dp))
            }
            if (state.sourceCount > 0) {
                MetaChip(if (state.sourceCount == 1) "1 source" else "${state.sourceCount} sources")
                Spacer(Modifier.width(8.dp))
            }
            HeaderButton("⌕")
            Spacer(Modifier.width(8.dp))
            HeaderButton("⚙")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Your playlists, your channels. Nothing is bundled.",
            style = LocalMatchdayType.current.meta,
            color = MatchdayColors.Dim,
        )
        Spacer(Modifier.height(14.dp))

        when {
            state.loading -> Text("Building the channel catalogue…", style = LocalMatchdayType.current.body, color = MatchdayColors.Muted)
            state.channels.isEmpty() -> AddPlaylist(state = state, onImport = vm::importPlaylist)
            else -> {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CategoryRail(
                        categories = state.categories,
                        selected = category,
                        onSelect = { category = it },
                        modifier = Modifier.width(226.dp),
                    )
                    Column(Modifier.weight(0.56f).fillMaxWidth()) {
                        SearchField(query = query, onQueryChange = { query = it })
                        Spacer(Modifier.height(8.dp))
                        ChannelTableHeader()
                        Spacer(Modifier.height(4.dp))
                        if (visible.isEmpty()) {
                            Text(
                                text = "No channels in this category.",
                                style = LocalMatchdayType.current.body,
                                color = MatchdayColors.Muted,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        } else {
                            ChannelTable(
                                channels = visible,
                                nowNext = state.nowNext,
                                onFocused = { selected = it },
                                onPlay = {
                                    vm.markWatched(it)
                                    onPlay(listOf(it))
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Column(Modifier.weight(0.40f).fillMaxWidth()) {
                        MediaPreview(
                            title = selected?.channel?.name ?: "Select a channel",
                            subtitle = selected?.categoryName ?: "Your playlist",
                            badge = selected?.source?.name,
                            modifier = Modifier.fillMaxWidth().height(198.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        ChannelInfo(
                            playable = selected,
                            nowNext = selected?.let { state.nowNext[it.channel.id] },
                            favorite = isFavorite(selected, state.favoriteRawIds),
                            onToggleFavorite = { selected?.let(vm::toggleFavorite) },
                        )
                    }
                }
            }
        }
    }
}

private fun matchesCategory(
    channel: PlayableChannel,
    key: String,
    favoriteIds: Set<Long>,
    recentIds: Set<Long>,
): Boolean = when (key) {
    "all" -> true
    "favorites" -> channel.logical?.variants?.any { it.rawChannelId in favoriteIds } == true
    "recent" -> channel.logical?.variants?.any { it.rawChannelId in recentIds } == true
    else -> channel.categoryName == key.removePrefix("cat:")
}

private fun isFavorite(channel: PlayableChannel?, favoriteIds: Set<Long>): Boolean =
    channel?.logical?.variants?.any { it.rawChannelId in favoriteIds } == true

@Composable
private fun HeaderButton(glyph: String) {
    Box(
        modifier = Modifier
            .background(MatchdayColors.Surface, RoundedCornerShape(50))
            .border(1.dp, MatchdayColors.CardLine, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = glyph, style = LocalMatchdayType.current.body, color = MatchdayColors.Muted)
    }
}

@Composable
private fun CategoryRail(
    categories: List<BrowseCategory>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        SectionLabel("BROWSE")
        Spacer(Modifier.height(7.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(categories, key = { it.key }) { category ->
                CategoryRow(
                    category = category,
                    selected = category.key == selected,
                    onClick = { onSelect(category.key) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(category: BrowseCategory, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .activationClick(onClick)
            .background(
                when {
                    focused -> MatchdayColors.Ivory
                    selected -> MatchdayColors.SurfaceRaised
                    else -> Color.Transparent
                },
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = category.label,
                style = LocalMatchdayType.current.meta.copy(fontSize = 13.sp),
                color = when {
                    focused -> MatchdayColors.Ink
                    selected -> MatchdayColors.Ivory
                    else -> MatchdayColors.Muted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = category.count.toString(),
                style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                color = if (focused) MatchdayColors.Ink else MatchdayColors.Dim,
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MatchdayColors.Surface, RoundedCornerShape(50))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MatchdayColors.Focus else MatchdayColors.CardLine,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = LocalMatchdayType.current.meta.copy(color = MatchdayColors.Ivory, fontSize = 14.sp),
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isBlank()) {
                    Text(
                        text = "⌕  Search channels",
                        style = LocalMatchdayType.current.meta,
                        color = MatchdayColors.Dim,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun ChannelTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("CHANNEL", style = LocalMatchdayType.current.label.copy(fontSize = 9.sp), color = MatchdayColors.Dim, modifier = Modifier.width(79.dp))
        Text("GROUP", style = LocalMatchdayType.current.label.copy(fontSize = 9.sp), color = MatchdayColors.Dim, modifier = Modifier.weight(1f))
        Text("SOURCE", style = LocalMatchdayType.current.label.copy(fontSize = 9.sp), color = MatchdayColors.Dim, modifier = Modifier.width(100.dp))
    }
}

@Composable
private fun ChannelTable(
    channels: List<PlayableChannel>,
    nowNext: Map<Long, NowNext>,
    onFocused: (PlayableChannel) -> Unit,
    onPlay: (PlayableChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(channels.firstOrNull()?.channel?.id) {
        delay(80)
        runCatching { first.requestFocus() }
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(bottom = 20.dp),
    ) {
        itemsIndexed(channels, key = { _, playable -> playable.channel.id }) { index, playable ->
            ChannelRow(
                playable = playable,
                nowNext = nowNext[playable.channel.id],
                onClick = { onPlay(playable) },
                onFocused = { onFocused(playable) },
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

@Composable
private fun ChannelRow(
    playable: PlayableChannel,
    nowNext: NowNext?,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        onFocused = onFocused,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = playable.channel.number?.toString().orEmpty().padStart(3, ' '),
                style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                color = MatchdayColors.Dim,
                modifier = Modifier.width(42.dp),
            )
            RemoteBadge(playable.channel.name, playable.channel.logoUrl, 28.dp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playable.channel.name,
                    style = LocalMatchdayType.current.subtitle.copy(fontSize = 15.sp),
                    color = MatchdayColors.Ivory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nowNext?.nowTitle ?: playable.categoryName ?: "Unsorted",
                    style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                    color = if (nowNext?.nowTitle != null) MatchdayColors.Muted else MatchdayColors.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = playable.source?.name ?: "Source",
                style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                color = MatchdayColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(100.dp),
            )
        }
    }
}

@Composable
private fun ChannelInfo(
    playable: PlayableChannel?,
    nowNext: NowNext?,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MatchdayColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, MatchdayColors.CardLine, RoundedCornerShape(12.dp))
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("SELECTED")
            Spacer(Modifier.weight(1f))
            FocusableSurface(
                onClick = onToggleFavorite,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = if (favorite) "★ Favourite" else "☆ Favourite",
                    style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
                    color = if (favorite) MatchdayColors.Brick else MatchdayColors.Muted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = nowNext?.nowTitle ?: playable?.channel?.name ?: "Select a channel",
            style = LocalMatchdayType.current.title.copy(fontSize = 19.sp),
            color = MatchdayColors.Ivory,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = nowNext?.let { next ->
                val nextPart = next.nextTitle?.let { "Next: $it" }
                listOfNotNull(playable?.categoryName, nextPart).joinToString("  ·  ")
            } ?: playable?.categoryName ?: "No guide data yet.",
            style = LocalMatchdayType.current.meta,
            color = MatchdayColors.Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaChip(playable?.source?.name ?: "No source")
            Spacer(Modifier.width(8.dp))
            playable?.logical?.variants?.firstOrNull()?.let { variant ->
                MetaChip(variant.quality.label.ifBlank { "AUTO" })
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "ENTER",
                style = LocalMatchdayType.current.label.copy(fontSize = 9.sp),
                color = MatchdayColors.Brick,
                maxLines = 1,
            )
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
                .width(500.dp)
                .background(MatchdayColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, MatchdayColors.CardLine, RoundedCornerShape(12.dp))
                .padding(22.dp),
        ) {
            SectionLabel("Add a source")
            Spacer(Modifier.height(14.dp))
            MatchdayTextField(
                value = name,
                onValueChange = { name = it },
                label = "Playlist name",
            )
            Spacer(Modifier.height(12.dp))
            MatchdayTextField(
                value = url,
                onValueChange = { url = it },
                label = if (BuildConfig.DEBUG) "M3U / M3U8 URL · local test server" else "M3U / M3U8 URL",
                keyboardType = KeyboardType.Uri,
            )
            Spacer(Modifier.height(16.dp))
            FocusableSurface(
                onClick = { onImport(name, url) },
                selected = true,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (state.importing) "Importing…" else "Import playlist",
                    style = LocalMatchdayType.current.subtitle.copy(fontSize = 16.sp),
                    color = MatchdayColors.Ivory,
                )
            }
            if (state.importing) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.progress ?: "Reading the playlist…",
                    style = LocalMatchdayType.current.meta,
                    color = MatchdayColors.Muted,
                )
            }
            state.error?.let { error ->
                Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MatchdayColors.Ink, RoundedCornerShape(8.dp))
                .border(
                    if (focused) 2.dp else 1.dp,
                    if (focused) MatchdayColors.Focus else MatchdayColors.CardLine,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
