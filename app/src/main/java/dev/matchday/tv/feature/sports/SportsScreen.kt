package dev.matchday.tv.feature.sports

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.tv.material3.Text
import dev.matchday.tv.BuildConfig
import dev.matchday.tv.design.EmptyState
import dev.matchday.tv.design.FocusableSurface
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.MetaChip
import dev.matchday.tv.design.SectionLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class PreviewEvent(
    val sport: String,
    val competition: String,
    val home: String,
    val away: String,
    val time: String,
    val streams: Int,
    val live: Boolean = false,
)

private val PreviewEvents = listOf(
    PreviewEvent("Football", "Premier League", "North City", "Riverside", "15:00", 3, live = true),
    PreviewEvent("Football", "Champions League", "Atlético Sur", "Olympique Nord", "20:00", 2),
    PreviewEvent("Tennis", "ATP Masters", "A. Moreau", "J. Weber", "13:30", 2),
    PreviewEvent("Motorsport", "Formula 1", "Monaco Grand Prix", "Race", "14:00", 4),
    PreviewEvent("Cycling", "UCI WorldTour", "Tour of the North", "Stage 4", "12:15", 1),
    PreviewEvent("Olympic sports", "World Aquatics", "Women's 200m final", "Final", "18:40", 2),
    PreviewEvent("Fighting", "UFC", "Silva", "Kowalski", "22:00", 3),
)

private val PreviewSports = listOf(
    "All",
    "Football",
    "Tennis",
    "Motorsport",
    "Cycling",
    "Olympic sports",
    "Fighting",
    "Other",
)

@Composable
fun SportsScreen(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) {
        EmptyState(
            eyebrow = "SCHEDULE",
            title = "Your sports day will live here.",
            body = "Free schedule sources arrive in the next milestone. Live TV and your playlists are already here.",
            modifier = modifier,
        )
        return
    }

    var selectedSport by remember { mutableStateOf("All") }
    val events = if (selectedSport == "All") PreviewEvents else PreviewEvents.filter { it.sport == selectedSport }
    val date = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "Today",
                style = LocalMatchdayType.current.display,
                color = MatchdayColors.Ivory,
            )
            Spacer(Modifier.width(18.dp))
            Text(
                text = date,
                style = LocalMatchdayType.current.subtitle,
                color = MatchdayColors.Muted,
            )
            Spacer(Modifier.width(18.dp))
            MetaChip("Design preview", accent = true)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Upcoming matches, games and races across the sports you follow.",
            style = LocalMatchdayType.current.body,
            color = MatchdayColors.Muted,
        )
        Spacer(Modifier.height(22.dp))
        SportTabs(selected = selectedSport, onSelect = { selectedSport = it })
        Spacer(Modifier.height(22.dp))

        if (events.isEmpty()) {
            EmptyState(
                eyebrow = "UPCOMING",
                title = "Nothing scheduled here yet.",
                body = "When a schedule source is connected, this is where the day's events will appear.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                item { HeroEvent(events.first()) }
                item {
                    SectionLabel("Upcoming · ${events.size}")
                }
                items(events.drop(1).chunked(2)) { rowEvents ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowEvents.forEach { event ->
                            EventCard(event, Modifier.weight(1f))
                        }
                        if (rowEvents.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SportTabs(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PreviewSports) { sport ->
            SportTab(
                label = sport,
                selected = sport == selected,
                onClick = { onSelect(sport) },
            )
        }
    }
}

@Composable
private fun SportTab(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
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
                shape,
            )
            .border(
                if (selected && !focused) 1.dp else 0.dp,
                if (selected && !focused) MatchdayColors.CardLine else Color.Transparent,
                shape,
            )
            .padding(horizontal = 17.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = LocalMatchdayType.current.meta.copy(fontSize = 13.sp),
            color = when {
                focused -> MatchdayColors.Ink
                selected -> MatchdayColors.Ivory
                else -> MatchdayColors.Muted
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun HeroEvent(event: PreviewEvent) {
    FocusableSurface(
        onClick = {},
        selected = true,
        shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(312.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(10.dp)
                    .height(312.dp)
                    .background(MatchdayColors.Brick),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 30.dp, vertical = 26.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.live) MetaChip("Live now", accent = true) else SectionLabel(event.time)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = event.competition.uppercase(),
                        style = LocalMatchdayType.current.label,
                        color = MatchdayColors.Muted,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = event.home,
                    style = LocalMatchdayType.current.display.copy(fontSize = 52.sp),
                    color = MatchdayColors.Ivory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "vs  ${event.away}",
                    style = LocalMatchdayType.current.title,
                    color = MatchdayColors.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetaChip(event.sport)
                    Spacer(Modifier.width(9.dp))
                    MetaChip("${event.streams} playable streams")
                }
            }
            Column(
                Modifier
                    .padding(end = 30.dp, top = 26.dp),
                horizontalAlignment = Alignment.End,
            ) {
                SectionLabel("MATCHDAY")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = event.time,
                    style = LocalMatchdayType.current.score.copy(fontSize = 38.sp),
                    color = MatchdayColors.Ivory,
                )
            }
        }
    }
}

@Composable
private fun EventCard(event: PreviewEvent, modifier: Modifier = Modifier) {
    FocusableSurface(
        onClick = {},
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (event.live) MetaChip("Live", accent = true) else SectionLabel(event.time)
                Spacer(Modifier.weight(1f))
                Text(
                    text = event.sport.uppercase(),
                    style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                    color = MatchdayColors.Dim,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = event.home,
                style = LocalMatchdayType.current.title.copy(fontSize = 26.sp),
                color = MatchdayColors.Ivory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = event.away,
                style = LocalMatchdayType.current.title.copy(fontSize = 26.sp),
                color = MatchdayColors.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = event.competition,
                style = LocalMatchdayType.current.meta,
                color = MatchdayColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = "${event.streams} streams",
                style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                color = MatchdayColors.Pitch,
            )
        }
    }
}
