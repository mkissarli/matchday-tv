package dev.matchday.tv.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import dev.matchday.tv.design.RemoteBadge
import dev.matchday.tv.design.SectionLabel
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val CHANNEL_COLUMN = 230

@Composable
fun GuideScreen(
    modifier: Modifier = Modifier,
    vm: GuideViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.loading) {
        Text("Reading the guide…", style = LocalMatchdayType.current.body, color = MatchdayColors.Muted, modifier = modifier)
        return
    }
    if (state.error != null) {
        Text(state.error ?: "", style = LocalMatchdayType.current.body, color = MatchdayColors.Brick, modifier = modifier)
        return
    }
    if (state.rows.isEmpty()) {
        EmptyState(
            eyebrow = "TV GUIDE",
            title = "The guide needs a source.",
            body = "Add an XMLTV guide URL to your playlist. Matchday keeps the last good copy and shows now/next across your channels.",
            modifier = modifier,
        )
        return
    }

    val slots = remember(state.fromMs, state.toMs) { timeSlots(state.fromMs, state.toMs) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "TV Guide",
                style = LocalMatchdayType.current.title.copy(fontSize = 30.sp),
                color = MatchdayColors.Ivory,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = "4 hours · ${state.rows.size} channels",
                style = LocalMatchdayType.current.meta,
                color = MatchdayColors.Muted,
            )
            Spacer(Modifier.weight(1f))
            MetaChip("LIVE")
            Spacer(Modifier.width(8.dp))
            FocusableSurface(
                onClick = vm::refresh,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text("Refresh", style = LocalMatchdayType.current.meta, color = MatchdayColors.Ivory)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(CHANNEL_COLUMN.dp)) {
                SectionLabel("CHANNEL")
            }
            Row(Modifier.weight(1f)) {
                slots.forEach { slot ->
                    Text(
                        text = slot,
                        style = LocalMatchdayType.current.label.copy(fontSize = 9.sp),
                        color = MatchdayColors.Dim,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(state.rows, key = { it.channel.id }) { row ->
                GuideChannelRow(
                    row = row,
                    fromMs = state.fromMs,
                    toMs = state.toMs,
                )
            }
        }
    }
}

@Composable
private fun GuideChannelRow(row: GuideRow, fromMs: Long, toMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MatchdayColors.Surface.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(CHANNEL_COLUMN.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteBadge(row.channel.name, row.channel.logoUrl, 25.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.channel.name,
                style = LocalMatchdayType.current.meta.copy(fontSize = 13.sp),
                color = MatchdayColors.Ivory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.weight(1f)) {
            if (row.programmes.isEmpty()) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(MatchdayColors.InkSoft, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "No guide data",
                        style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                        color = MatchdayColors.Dim,
                    )
                }
            } else {
                val blocks = programmeBlocks(row.programmes, fromMs, toMs)
                blocks.forEach { block ->
                    when (block) {
                        is GuideBlock.Gap -> Spacer(Modifier.weight(block.minutes.coerceAtLeast(1f)))
                        is GuideBlock.Programme -> ProgrammeBlock(block.programme, block.minutes)
                    }
                }
            }
        }
    }
}

private sealed interface GuideBlock {
    data class Gap(val minutes: Float) : GuideBlock
    data class Programme(val programme: EpgProgrammeEntity, val minutes: Float) : GuideBlock
}

private fun programmeBlocks(programmes: List<EpgProgrammeEntity>, fromMs: Long, toMs: Long): List<GuideBlock> {
    val out = mutableListOf<GuideBlock>()
    var cursor = fromMs
    programmes.sortedBy { it.startMs }.forEach { programme ->
        val start = programme.startMs.coerceAtLeast(fromMs)
        val stop = programme.stopMs.coerceAtMost(toMs)
        if (stop <= start) return@forEach
        // A programme that started well before the window is mostly off-screen. Showing a
        // 10-pixel sliver of it is worse than a clean gap, so skip only the tiny leftovers.
        if (stop - start < 20 * 60_000L) {
            cursor = maxOf(cursor, stop)
            return@forEach
        }
        if (start > cursor) out += GuideBlock.Gap((start - cursor) / 60_000f)
        out += GuideBlock.Programme(programme, (stop - start) / 60_000f)
        cursor = stop
    }
    if (cursor < toMs) out += GuideBlock.Gap((toMs - cursor) / 60_000f)
    return out
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ProgrammeBlock(
    programme: EpgProgrammeEntity,
    minutes: Float,
) {
    val now = System.currentTimeMillis()
    val current = programme.startMs <= now && programme.stopMs > now
    Box(
        Modifier
            .weight(minutes.coerceAtLeast(1f))
            .height(40.dp)
            .padding(horizontal = 1.dp)
            .background(
                if (current) MatchdayColors.Pitch.copy(alpha = 0.35f) else MatchdayColors.SurfaceRaised,
                RoundedCornerShape(4.dp),
            )
            .border(
                1.dp,
                if (current) MatchdayColors.Pitch else MatchdayColors.CardLine,
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Column {
            Text(
                text = programme.title,
                style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
                color = if (current) MatchdayColors.Ivory else MatchdayColors.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = time(programme.startMs),
                style = LocalMatchdayType.current.meta.copy(fontSize = 10.sp),
                color = MatchdayColors.Dim,
                maxLines = 1,
            )
        }
    }
}

private fun time(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun timeSlots(fromMs: Long, toMs: Long): List<String> {
    val out = mutableListOf<String>()
    var t = fromMs
    while (t < toMs) {
        out += time(t)
        t += 30 * 60_000L
    }
    return out
}
