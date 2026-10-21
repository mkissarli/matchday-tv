package dev.matchday.tv.feature.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.matchday.domain.catalog.LogicalChannel
import dev.matchday.tv.design.FocusableSurface
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.MetaChip
import org.koin.androidx.compose.koinViewModel

private const val ALL = "__all__"
private const val LOW = "__low__"
private const val UNSORTED = "__unsorted__"

@Composable
fun CatalogDebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: CatalogDebugViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(ALL) }
    BackHandler(onBack = onBack)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Catalog debug",
                style = LocalMatchdayType.current.title.copy(fontSize = 28.sp),
                color = MatchdayColors.Ivory,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = "normalisation · merging · language · genre",
                style = LocalMatchdayType.current.meta,
                color = MatchdayColors.Dim,
            )
            Spacer(Modifier.weight(1f))
            MetaChip("BACK", accent = false)
            Spacer(Modifier.width(8.dp))
            FocusableSurface(
                onClick = vm::refresh,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text("Refresh", style = LocalMatchdayType.current.meta, color = MatchdayColors.Ivory)
            }
        }
        Spacer(Modifier.height(8.dp))

        val result = state.result
        if (result == null) {
            Text(
                text = if (state.loading) "Building the derived catalogue…" else (state.error ?: "No data."),
                style = LocalMatchdayType.current.body,
                color = if (state.error != null) MatchdayColors.Brick else MatchdayColors.Muted,
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip("${result.stats.rawCount} raw")
            MetaChip("${result.stats.logicalCount} logical")
            MetaChip("${result.stats.mergedCount} merged")
            MetaChip("${result.stats.unsortedCount} unsorted")
            MetaChip("${result.stats.lowConfidenceCount} low confidence", accent = result.stats.lowConfidenceCount > 0)
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val categories = remember(result, selected) { categoriesFor(result.channels) }
            LazyColumn(
                modifier = Modifier.width(300.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(categories) { category ->
                    DebugCategoryRow(
                        label = category.label,
                        count = category.count,
                        selected = category.key == selected,
                        onClick = { selected = category.key },
                    )
                }
            }
            val visible = remember(result, selected) {
                when (selected) {
                    ALL -> result.channels
                    LOW -> result.lowConfidence
                    UNSORTED -> result.unsorted
                    else -> result.channels.filter { categoryKey(it) == selected }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(visible, key = { it.key }) { channel ->
                    DebugChannelRow(channel)
                }
            }
        }
    }
}

private data class DebugCategory(val key: String, val label: String, val count: Int)

private fun categoriesFor(channels: List<LogicalChannel>): List<DebugCategory> {
    val grouped = channels.groupBy(::categoryKey)
        .map { (key, list) -> DebugCategory(key, list.first().let { "${it.language?.label ?: "Unknown language"} → ${it.genre.label}" }, list.size) }
        .sortedBy { it.label }
    return buildList {
        add(DebugCategory(ALL, "All logical channels", channels.size))
        add(DebugCategory(LOW, "Low confidence merges", channels.count { it.variants.size > 1 && it.confidence < 0.85 }))
        add(DebugCategory(UNSORTED, "Unsorted", channels.count { it.unsorted }))
        addAll(grouped)
    }
}

private fun categoryKey(channel: LogicalChannel): String =
    "${channel.language?.code ?: "??"}/${channel.genre.id}"

@Composable
private fun DebugCategoryRow(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .background(
                when {
                    focused -> MatchdayColors.Ivory
                    selected -> MatchdayColors.SurfaceRaised
                    else -> Color.Transparent
                },
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
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
                text = count.toString(),
                style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
                color = if (focused) MatchdayColors.Ink else MatchdayColors.Dim,
            )
        }
    }
}

@Composable
private fun DebugChannelRow(channel: LogicalChannel) {
    val variantText = channel.variants.joinToString("  ·  ") { variant ->
        listOfNotNull(
            variant.quality.label.ifBlank { "?" },
            variant.codec,
            "source ${variant.sourceId}",
        ).joinToString(" ")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MatchdayColors.Surface, RoundedCornerShape(7.dp))
            .border(
                1.dp,
                if (channel.confidence < 0.85 && channel.variants.size > 1) MatchdayColors.Brick.copy(alpha = 0.55f) else MatchdayColors.CardLine,
                RoundedCornerShape(7.dp),
            )
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = channel.name,
                style = LocalMatchdayType.current.subtitle.copy(fontSize = 16.sp),
                color = MatchdayColors.Ivory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(channel.confidence * 100).toInt()}%",
                style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
                color = if (channel.confidence < 0.85) MatchdayColors.Brick else MatchdayColors.Pitch,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = channel.language?.code ?: "??",
                style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                color = MatchdayColors.Muted,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = channel.genre.label,
                style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                color = if (channel.unsorted) MatchdayColors.Dim else MatchdayColors.Muted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = variantText,
            style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
            color = MatchdayColors.Dim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
