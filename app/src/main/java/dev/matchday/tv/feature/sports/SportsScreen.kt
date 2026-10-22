package dev.matchday.tv.feature.sports

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.matchday.domain.schedule.Participant
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.LeaguePopularity
import dev.matchday.domain.schedule.SportRegistry
import dev.matchday.domain.schedule.SportRegistryEntry
import dev.matchday.tv.design.EmptyState
import dev.matchday.tv.design.FocusableSurface
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.MetaChip
import dev.matchday.tv.design.RemoteBadge
import dev.matchday.tv.design.SportIcon
import dev.matchday.tv.design.activationClick
import dev.matchday.tv.feature.live.PlayableChannel
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SportsScreen(
    onPlay: (List<PlayableChannel>) -> Unit,
    onOpenChannels: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: SportsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var collapsedLeagues by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMatches by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("all") } // "all" | "live"
    // Read this in the composable body so toggling a header actually invalidates the
    // LazyColumn content (the LazyListScope lambda itself is not a composable).
    val collapsedSnapshot = collapsedLeagues
    val filtered = remember(state.events, state.selectedSportId) {
        if (state.selectedSportId == "all") state.events
        else state.events.filter { it.sportId == state.selectedSportId }
    }
    val leagueOptions = remember(filtered) {
        filtered.groupBy { eventLabel(it) }.keys
            .sortedWith(compareBy({ LeaguePopularity.rank(it) }, { it }))
    }
    val visibleEvents = remember(filtered, statusFilter) {
        when (statusFilter) {
            "live" -> filtered.filter { isLive(it) }
            else -> filtered
        }
    }
    LaunchedEffect(state.selectedSportId) { collapsedLeagues = emptySet() }
    LaunchedEffect(leagueOptions) { collapsedLeagues = collapsedLeagues.filter { it in leagueOptions }.toSet() }

    // Per day, then per league (biggest first), matches in time order within each league.
    // Ongoing multi-day events (tournaments, race weekends) started days ago but are
    // still current — pin them under today's band instead of a stale past date.
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val dayGroups = remember(visibleEvents, today) {
        val zone = ZoneId.systemDefault()
        visibleEvents
            .groupBy {
                val start = Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate()
                if (start < today) today else start
            }
            .map { (date, dayEvents) ->
                val leagues = dayEvents
                    .groupBy { eventLabel(it) }
                    .map { (league, leagueEvents) ->
                        league to leagueEvents.sortedBy { it.startMs }
                    }
                    .sortedWith(
                        compareBy(
                            { (league, _) -> LeaguePopularity.rank(league) },
                            { (_, events) -> events.firstOrNull()?.startMs ?: Long.MAX_VALUE },
                        ),
                    )
                DayGroup(date, leagues)
            }
            .sortedBy { it.date }
    }
    val flatEvents = remember(dayGroups) { dayGroups.flatMap { day -> day.leagues.flatMap { it.second } } }
    LaunchedEffect(flatEvents) {
        if (flatEvents.none { it.id == selectedId }) selectedId = flatEvents.firstOrNull()?.id
    }
    val selected = flatEvents.firstOrNull { it.id == selectedId }
    val liveCount = filtered.count { isLive(it) }

    Column(modifier = modifier.fillMaxWidth()) {
        SportsHeader(
            count = visibleEvents.size,
            liveCount = liveCount,
            attribution = state.attribution,
            onRefresh = vm::refresh,
        )
        Spacer(Modifier.height(12.dp))
        SportCoinRow(
            sports = state.sports,
            selected = state.selectedSportId,
            onSelect = vm::selectSport,
        )
        Spacer(Modifier.height(14.dp))
        StatusPillRow(
            totalCount = filtered.size,
            liveCount = liveCount,
            selected = statusFilter,
            onSelect = { statusFilter = it },
        )
        Spacer(Modifier.height(12.dp))
        when {
            state.loading && state.events.isEmpty() -> {
                Text("Loading the schedule…", style = LocalMatchdayType.current.body, color = MatchdayColors.Muted)
            }
            state.error != null -> {
                EmptyState(
                    eyebrow = "SCHEDULE",
                    title = "Schedule unavailable.",
                    body = "The schedule source could not be reached: ${state.error}",
                    action = {
                        FocusableSurface(onClick = vm::refresh, shape = RoundedCornerShape(8.dp)) {
                            Text("Retry", style = LocalMatchdayType.current.subtitle, color = MatchdayColors.Ivory)
                        }
                    },
                )
            }
            visibleEvents.isEmpty() -> {
                EmptyState(
                    eyebrow = "SCHEDULE",
                    title = if (statusFilter == "live") "Nothing live right now." else "No events in the next seven days.",
                    body = if (statusFilter == "live") "Choose All to see the full week's fixtures." else
                        "The schedule source returned nothing for this sport. Choose All or refresh.",
                )
            }
            else -> {
                val zone = ZoneId.systemDefault()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    dayGroups.forEach { day ->
                        val dayEvents = day.leagues.flatMap { it.second }
                        item(key = "day:${day.date}") {
                            DayBand(
                                label = dayLabel(day.date, today),
                                count = dayEvents.size,
                                liveCount = dayEvents.count { isLive(it) },
                            )
                        }
                        day.leagues.forEach { (league, events) ->
                            val collapsed = league in collapsedSnapshot
                            item(key = "league:${day.date}:$league") {
                                LeagueBand(
                                    label = league,
                                    count = events.size,
                                    collapsed = collapsed,
                                    onToggle = {
                                        collapsedLeagues =
                                            if (collapsed) collapsedLeagues - league else collapsedLeagues + league
                                    },
                                )
                            }
                            if (!collapsed) {
                                items(events, key = { "${day.date}:$league:${it.id}" }) { event ->
                                    MatchRow(
                                        event = event,
                                        selected = event.id == selected?.id,
                                        showDay = false,
                                        onFocused = { selectedId = event.id },
                                        onClick = {
                                            selectedId = event.id
                                            vm.openMatch(event)
                                            showMatches = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMatches && selected != null) {
        MatchDialog(
            event = selected,
            listing = state.listings[selected.id].orEmpty(),
            lookupLoading = state.lookupEventId == selected.id,
            lookupFailure = state.lookupFailures[selected.id],
            deepMatches = state.matches[selected.id].orEmpty(),
            deepLoading = state.matchingEventId == selected.id,
            deepError = state.matchError,
            onDismiss = { showMatches = false },
            onDeepMatch = { vm.findChannels(selected) },
            onPlay = { chosen, rest ->
                showMatches = false
                onPlay(listOf(chosen) + rest)
            },
        )
    }
}

private data class DayGroup(
    val date: LocalDate,
    val leagues: List<Pair<String, List<ScheduleEvent>>>,
)

private fun dayLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date == today.plusDays(1) -> "Tomorrow"
    else -> date.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
}

@Composable
private fun SportsHeader(
    count: Int,
    liveCount: Int,
    attribution: String,
    onRefresh: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Sports Hub", style = LocalMatchdayType.current.title.copy(fontSize = 30.sp), color = MatchdayColors.Ivory)
        Spacer(Modifier.width(30.dp))
        // The old "Channels" tab opened the Live TV section; that section is
        // parked, so the tab is gone (the callback stays for a comeback).
        Spacer(Modifier.weight(1f))
        Text(
            text = "$liveCount live · $count this week",
            style = LocalMatchdayType.current.meta,
            color = MatchdayColors.Muted,
        )
        Spacer(Modifier.width(14.dp))
        MetaChip("Next 7 days")
        Spacer(Modifier.width(8.dp))
        FocusableSurface(
            onClick = onRefresh,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text("↻", style = LocalMatchdayType.current.body, color = MatchdayColors.Muted)
        }
    }
}

/** Round sport coins across the top, FCTV-style: filled coin for the chosen sport. */
@Composable
private fun SportCoinRow(
    sports: List<SportRegistryEntry>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val entries = remember(sports) {
        // "All" leads the coins; iconKey "other" renders a layered ball for it.
        listOf(SportRegistryEntry("all", "All", "other", -1, emptyList(), dev.matchday.domain.schedule.ParticipantStyle.EVENT)) + sports
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(entries, key = { it.id }) { sport ->
            SportCoin(
                label = sport.displayName,
                iconKey = sport.iconKey,
                selected = sport.id == selected,
                onClick = { onSelect(sport.id) },
            )
        }
    }
}

@Composable
private fun SportCoin(label: String, iconKey: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .activationClick(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(56.dp)
                .background(
                    when {
                        selected -> MatchdayColors.Brick
                        focused -> MatchdayColors.SurfaceRaised
                        else -> MatchdayColors.Surface
                    },
                    RoundedCornerShape(50),
                )
                .border(
                    when {
                        selected -> 0.dp
                        focused -> 3.dp
                        else -> 1.dp
                    },
                    when {
                        selected -> Color.Transparent
                        focused -> MatchdayColors.Focus
                        else -> MatchdayColors.CardLine
                    },
                    RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            SportIcon(
                sport = iconKey,
                modifier = Modifier.width(26.dp).height(26.dp),
                tint = when {
                    selected -> MatchdayColors.Ivory
                    focused -> MatchdayColors.Ivory
                    else -> MatchdayColors.Muted
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
            color = if (selected || focused) MatchdayColors.Ivory else MatchdayColors.Muted,
            maxLines = 1,
        )
    }
}

/** All | Live(n) status pills under the coins. */
@Composable
private fun StatusPillRow(
    totalCount: Int,
    liveCount: Int,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(label = "All", selected = selected == "all") { onSelect("all") }
            StatusPill(
                label = "Live ($liveCount)",
                selected = selected == "live",
                accent = true,
                enabled = liveCount > 0 || selected == "live",
            ) { onSelect("live") }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$totalCount in the next 7 days",
            style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
            color = MatchdayColors.Dim,
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    selected: Boolean,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .activationClick { if (enabled) onClick() }
            .alpha(if (enabled) 1f else 0.45f)
            .background(
                when {
                    selected -> if (accent) MatchdayColors.Brick else MatchdayColors.SurfaceRaised
                    focused -> MatchdayColors.SurfaceRaised
                    else -> MatchdayColors.Surface
                },
                shape,
            )
            .border(
                if (selected) 0.dp else 1.dp,
                when {
                    selected -> Color.Transparent
                    accent -> MatchdayColors.BrickSoft
                    else -> MatchdayColors.CardLine
                },
                shape,
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = LocalMatchdayType.current.meta.copy(fontSize = 13.sp),
            color = when {
                selected -> MatchdayColors.Ivory
                focused -> MatchdayColors.Ivory
                else -> MatchdayColors.Muted
            },
            maxLines = 1,
        )
    }
}

/** "TODAY · 21 OCT" separator band. */
@Composable
private fun DayBand(label: String, count: Int, liveCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(MatchdayColors.InkSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = LocalMatchdayType.current.label.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
            color = MatchdayColors.Ivory,
        )
        Spacer(Modifier.width(10.dp))
        if (liveCount > 0) {
            MetaChip("LIVE $liveCount", accent = true)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = count.toString(),
            style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
            color = MatchdayColors.Dim,
        )
    }
}

/** League band inside a day, collapsible. */
@Composable
private fun LeagueBand(
    label: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    FocusableSurface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (collapsed) "▸" else "▾",
                style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
                color = MatchdayColors.Brick,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = LocalMatchdayType.current.label.copy(fontSize = 11.sp, letterSpacing = 1.sp),
                color = MatchdayColors.Brick,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                color = MatchdayColors.Dim,
            )
        }
    }
}

/** One fixture as a dense list row: time rail | stacked teams | broadcaster + play affordance. */
@Composable
private fun MatchRow(
    event: ScheduleEvent,
    selected: Boolean,
    showDay: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val live = isLive(event)
    FocusableSurface(
        onClick = onClick,
        onFocused = onFocused,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // time / live rail, like FCTV33's left column
            Column(Modifier.width(58.dp)) {
                if (live) {
                    Text(
                        text = "LIVE",
                        style = LocalMatchdayType.current.label.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                        color = Color(0xFF7FC48A),
                    )
                } else {
                    Text(
                        text = time(event.startMs),
                        style = LocalMatchdayType.current.subtitle.copy(fontSize = 14.sp),
                        color = MatchdayColors.Body,
                        maxLines = 1,
                    )
                    if (showDay) {
                        Text(
                            text = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault())
                                .toLocalDate().format(DateTimeFormatter.ofPattern("EEE d MMM")),
                            style = LocalMatchdayType.current.meta.copy(fontSize = 10.sp),
                            color = MatchdayColors.Dim,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            // stacked team lines with badges
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (event.participants.size >= 2) {
                    event.participants.take(2).forEach { participant -> TeamLine(participant) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SportIcon(event.sportId, modifier = Modifier.width(15.dp).height(15.dp), tint = MatchdayColors.Muted)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = displayTitle(event),
                            style = LocalMatchdayType.current.subtitle.copy(fontSize = 15.sp),
                            color = MatchdayColors.Ivory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // right rail: where it airs + play affordance
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = event.broadcaster ?: event.venue ?: "",
                    style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                    color = MatchdayColors.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 170.dp),
                )
                Box(
                    modifier = Modifier
                        .background(MatchdayColors.Brick, RoundedCornerShape(7.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "▶ CH",
                        style = LocalMatchdayType.current.label.copy(fontSize = 9.sp),
                        color = MatchdayColors.Ivory,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamLine(participant: Participant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RemoteBadge(
            name = participant.name,
            logoUrl = participant.logoUrl ?: participant.portraitUrl,
            size = 20.dp,
            accent = teamColor(participant.id.hashCode()),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = participant.name,
            style = LocalMatchdayType.current.subtitle.copy(fontSize = 15.sp),
            color = MatchdayColors.Ivory,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun displayTitle(event: ScheduleEvent): String =
    if (event.participants.size >= 2) {
        event.participants.take(2).joinToString("  vs  ") { it.name }
    } else {
        event.title
    }

private fun eventLabel(event: ScheduleEvent): String =
    event.competitionName?.takeIf { it.isNotBlank() }
        ?: SportRegistry.all.firstOrNull { it.id == event.sportId }?.displayName
        ?: event.sportId.replace('-', ' ')

private fun isLive(event: ScheduleEvent): Boolean {
    val now = System.currentTimeMillis()
    val end = event.endMs ?: (event.startMs + 3 * 60 * 60_000L)
    return now in event.startMs..end
}

private fun time(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun teamColor(seed: Int): Color {
    val palette = listOf(
        Color(0xFF4E7F62),
        Color(0xFFB85C45),
        Color(0xFF8B6B3F),
        Color(0xFF4F6F86),
        Color(0xFF7A5B7E),
    )
    return palette[(seed and Int.MAX_VALUE) % palette.size]
}

@Composable
private fun MatchDialog(
    event: ScheduleEvent,
    listing: List<dev.matchday.tv.data.SuggestedRow>,
    lookupLoading: Boolean,
    lookupFailure: String?,
    deepMatches: List<dev.matchday.tv.data.MatchedChannel>,
    deepLoading: Boolean,
    deepError: String?,
    onDismiss: () -> Unit,
    onPlay: (PlayableChannel, List<PlayableChannel>) -> Unit,
    onDeepMatch: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        BackHandler(onBack = onDismiss)
        val first = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(listing, deepMatches, lookupLoading, deepLoading) {
            if (!lookupLoading && !deepLoading) {
                kotlinx.coroutines.delay(100)
                runCatching { first.requestFocus() }
            }
        }
        // Every matched user channel appears in the fallback playlist in ranking order.
        val fallthrough = remember(listing, deepMatches) {
            val seen = HashSet<Long>()
            buildList {
                listing.forEach { row ->
                    row.channels.forEach { channel ->
                        if (seen.add(channel.id)) add(PlayableChannel(channel, null, null))
                    }
                }
                deepMatches.forEach { match ->
                    if (seen.add(match.channel.id)) add(match.playable())
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xF20E0F10))
                .padding(horizontal = 58.dp, vertical = 38.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SportIcon(event.sportId, modifier = Modifier.width(22.dp).height(22.dp), tint = MatchdayColors.Muted)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = displayTitle(event),
                        style = LocalMatchdayType.current.title.copy(fontSize = 28.sp),
                        color = MatchdayColors.Ivory,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(14.dp))
                    MetaChip(eventLabel(event))
                    Spacer(Modifier.weight(1f))
                    FocusableSurface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "CLOSE",
                            style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                            color = MatchdayColors.Muted,
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "${time(event.startMs)}  ·  ${event.broadcaster ?: "No broadcaster listed"}  ·  Broadcast data by LiveSoccerTV",
                    style = LocalMatchdayType.current.meta,
                    color = MatchdayColors.Muted,
                )
                Spacer(Modifier.height(16.dp))
                val playRow: (PlayableChannel) -> Unit = { chosen ->
                    onPlay(chosen, fallthrough.filterNot { it.channel.id == chosen.channel.id })
                }
                when {
                    lookupLoading -> Text(
                        "Looking up broadcasters…",
                        style = LocalMatchdayType.current.body,
                        color = MatchdayColors.Muted,
                    )
                    listing.isEmpty() -> NoSuggestions(
                        event = event,
                        lookupFailure = lookupFailure,
                        deepMatches = deepMatches,
                        deepLoading = deepLoading,
                        deepError = deepError,
                        onPlayRow = playRow,
                        onDeepMatch = onDeepMatch,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        var previousCountry = ""
                        var firstInteractiveSeen = false
                        listing.forEach { row ->
                            if (row.broadcast.country != previousCountry) {
                                previousCountry = row.broadcast.country
                                item(key = "country:$previousCountry") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = previousCountry.uppercase(),
                                            style = LocalMatchdayType.current.label.copy(fontSize = 11.sp, letterSpacing = 1.1.sp),
                                            color = MatchdayColors.Brick,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = tierLabel(row.broadcast.tier),
                                            style = LocalMatchdayType.current.meta.copy(fontSize = 10.sp),
                                            color = MatchdayColors.Dim,
                                        )
                                    }
                                }
                            }
                            val focusTarget = row.channels.isNotEmpty() && !firstInteractiveSeen
                            if (focusTarget) firstInteractiveSeen = true
                            item(key = "broadcast:${row.broadcast.country}:${row.broadcast.channelName}") {
                                BroadcastRowPanel(row, if (focusTarget) Modifier.focusRequester(first) else null, playRow)
                            }
                        }
                        item(key = "deep-section") {
                            DeepSection(deepMatches, deepLoading, deepError, playRow, onDeepMatch)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BroadcastRowPanel(
    row: dev.matchday.tv.data.SuggestedRow,
    focusModifier: Modifier?,
    onPlayRow: (PlayableChannel) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = row.broadcast.channelName,
            style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
            color = MatchdayColors.Dim,
        )
        Spacer(Modifier.height(4.dp))
        if (row.channels.isEmpty()) {
            Text(
                text = "not in your sources",
                style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                color = MatchdayColors.Dim.copy(alpha = 0.6f),
            )
        } else {
            row.channels.forEachIndexed { index, channel ->
                FocusableSurface(
                    onClick = { onPlayRow(PlayableChannel(channel, null, null)) },
                    onFocused = {},
                    modifier = if (index == 0 && focusModifier != null) focusModifier else Modifier,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RemoteBadge(channel.name, channel.logoUrl, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = dev.matchday.domain.catalog.NameNormalizer.display(channel.name),
                            style = LocalMatchdayType.current.subtitle.copy(fontSize = 16.sp),
                            color = MatchdayColors.Ivory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "via ${row.broadcast.channelName}",
                            style = LocalMatchdayType.current.meta.copy(fontSize = 10.sp),
                            color = MatchdayColors.Dim,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun DeepSection(
    deepMatches: List<dev.matchday.tv.data.MatchedChannel>,
    deepLoading: Boolean,
    deepError: String?,
    onPlayRow: (PlayableChannel) -> Unit,
    onDeepMatch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MatchdayColors.CardLine))
        Spacer(Modifier.height(10.dp))
        Text(
            text = "GUIDE MATCH",
            style = LocalMatchdayType.current.label.copy(fontSize = 11.sp, letterSpacing = 1.1.sp),
            color = MatchdayColors.Brick,
        )
        Spacer(Modifier.height(6.dp))
        when {
            deepLoading -> Text(
                "Deep matching your guide…",
                style = LocalMatchdayType.current.body,
                color = MatchdayColors.Muted,
            )
            deepError != null -> Text(
                "Deep match failed: $deepError",
                style = LocalMatchdayType.current.meta,
                color = MatchdayColors.Brick,
            )
            deepMatches.isEmpty() -> Text(
                text = "Final-hope search scans titles of your guide for this fixture.",
                style = LocalMatchdayType.current.meta,
                color = MatchdayColors.Dim,
            )
            else -> Text(
                text = "${deepMatches.size} candidate channel(s) from your guide:",
                style = LocalMatchdayType.current.meta,
                color = MatchdayColors.Muted,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (!deepLoading) {
            FocusableSurface(
                onClick = onDeepMatch,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Search guide & EPG",
                    style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                    color = MatchdayColors.Ivory,
                )
            }
        }
        deepMatches.forEach { match ->
            Spacer(Modifier.height(4.dp))
            FocusableSurface(
                onClick = { onPlayRow(match.playable()) },
                onFocused = {},
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteBadge(match.channel.name, match.channel.logoUrl, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = match.channel.name,
                            style = LocalMatchdayType.current.subtitle.copy(fontSize = 15.sp),
                            color = MatchdayColors.Ivory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = match.match.reasons.joinToString(" · "),
                            style = LocalMatchdayType.current.meta.copy(fontSize = 10.sp),
                            color = MatchdayColors.Dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (match.match.autoPlayEligible) MetaChip("AUTO", accent = true)
                }
            }
        }
    }
}

@Composable
private fun NoSuggestions(
    event: ScheduleEvent,
    lookupFailure: String?,
    deepMatches: List<dev.matchday.tv.data.MatchedChannel>,
    deepLoading: Boolean,
    deepError: String?,
    onPlayRow: (PlayableChannel) -> Unit,
    onDeepMatch: () -> Unit,
) {
    Column(Modifier.width(840.dp)) {
        Text(
            text = when {
                lookupFailure != null -> "No broadcaster listing found for this fixture."
                event.sportId != "football" -> "Broadcast listings cover football only."
                else -> "No broadcaster options came back for this fixture."
            },
            style = LocalMatchdayType.current.body,
            color = MatchdayColors.Muted,
        )
        Spacer(Modifier.height(12.dp))
        DeepSection(deepMatches, deepLoading, deepError, onPlayRow, onDeepMatch)
    }
}

private fun tierLabel(tier: Int): String = when (tier) {
    1 -> "UK"
    2 -> "US"
    3 -> "FR"
    4 -> "ARABIC"
    5 -> "GERMAN"
    else -> "OTHER"
}
