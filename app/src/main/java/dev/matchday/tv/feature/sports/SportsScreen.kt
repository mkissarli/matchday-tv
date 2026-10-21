package dev.matchday.tv.feature.sports

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.focusRequester
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
import dev.matchday.domain.schedule.Participant
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.LeaguePopularity
import dev.matchday.domain.schedule.SportRegistry
import dev.matchday.domain.schedule.SportRegistryEntry
import dev.matchday.domain.schedule.TeamPopularity
import dev.matchday.tv.design.EmptyState
import dev.matchday.tv.design.FocusableSurface
import dev.matchday.tv.design.LocalMatchdayType
import dev.matchday.tv.design.MatchdayColors
import dev.matchday.tv.design.MediaPreview
import dev.matchday.tv.design.MetaChip
import dev.matchday.tv.design.RemoteBadge
import dev.matchday.tv.design.SectionLabel
import dev.matchday.tv.design.SportIcon
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
    var selectedLeague by remember { mutableStateOf<String?>(null) }
    var collapsedLeagues by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMatches by remember { mutableStateOf(false) }
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
    val visibleEvents = remember(filtered, selectedLeague) {
        if (selectedLeague == null) filtered else filtered.filter { eventLabel(it) == selectedLeague }
    }
    LaunchedEffect(state.selectedSportId) {
        selectedLeague = null
        collapsedLeagues = emptySet()
    }
    LaunchedEffect(leagueOptions) {
        if (selectedLeague != null && selectedLeague !in leagueOptions) selectedLeague = null
    }
    val orderedEvents = remember(visibleEvents) {
        visibleEvents.sortedWith(
            compareBy(
                { sportRank(it.sportId) },
                { event ->
                    // Within tennis, put the biggest names first; other sports stay chronological.
                    if (event.sportId == "tennis") -TeamPopularity.boost(event.participants) else 0
                },
                { it.startMs },
            ),
        )
    }
    LaunchedEffect(orderedEvents) {
        if (orderedEvents.none { it.id == selectedId }) selectedId = orderedEvents.firstOrNull()?.id
    }
    val selected = orderedEvents.firstOrNull { it.id == selectedId } ?: orderedEvents.firstOrNull()
    val grouped = remember(orderedEvents) {
        orderedEvents.groupBy { eventLabel(it) }
            .toList()
            .sortedWith(
                compareBy(
                    { (league, _) -> LeaguePopularity.rank(league) },
                    { (_, events) ->
                        val first = events.firstOrNull()
                        if (first == null) Int.MAX_VALUE else sportRank(first.sportId)
                    },
                    { (_, events) -> events.minOfOrNull { it.startMs } ?: Long.MAX_VALUE },
                ),
            )
    }
    val headlineEvents = remember(orderedEvents) {
        val candidates = orderedEvents.sortedWith(
            compareBy(
                { event ->
                    val liveBump = if (isLive(event)) 8 else 0
                    LeaguePopularity.rank(eventLabel(event)) - liveBump -
                        TeamPopularity.boost(event.participants)
                },
                { it.startMs },
            ),
        )
        val picked = ArrayList<ScheduleEvent>(3)
        val usedTeams = HashSet<String>()
        candidates.forEach { event ->
            if (picked.size >= 3) return@forEach
            val names = event.participants
                .map { it.name.lowercase(java.util.Locale.ROOT).trim() }
                .filter { it.isNotBlank() }
            if (names.any { it in usedTeams }) return@forEach
            names.forEach { usedTeams += it }
            picked += event
        }
        if (picked.size < 3) {
            candidates.forEach { event ->
                if (picked.size < 3 && picked.none { it.id == event.id }) picked += event
            }
        }
        picked
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "sports-header") {
            SportsHeader(
                count = visibleEvents.size,
                liveCount = visibleEvents.count { isLive(it) },
                attribution = state.attribution,
                onRefresh = vm::refresh,
                onOpenChannels = onOpenChannels,
            )
        }
        item(key = "sports-tabs") {
            SportTabs(
                sports = state.sports,
                selected = state.selectedSportId,
                onSelect = vm::selectSport,
            )
        }
        if (leagueOptions.size > 1) {
            item(key = "sports-leagues") {
                LeagueFilterRow(
                    leagues = leagueOptions,
                    selected = selectedLeague,
                    onSelect = { selectedLeague = it },
                )
            }
        }
        if (state.loading && state.events.isEmpty()) {
            item(key = "sports-loading") {
                Text("Loading the schedule…", style = LocalMatchdayType.current.body, color = MatchdayColors.Muted)
            }
        } else if (state.error != null) {
            item(key = "sports-error") {
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
        } else if (visibleEvents.isEmpty()) {
            item(key = "sports-empty") {
                EmptyState(
                    eyebrow = "SCHEDULE",
                    title = "No events in the next seven days.",
                    body = "The schedule source returned nothing for this sport. Choose All or refresh.",
                )
            }
        } else {
            item(key = "sports-headlines") {
                Column {
                    SectionLabel("HEADLINES")
                    Spacer(Modifier.height(4.dp))
                    HeadlineRow(
                        events = headlineEvents,
                        selectedId = selected?.id,
                        onFocus = { selectedId = it.id },
                        onOpen = { event ->
                            selectedId = event.id
                            vm.findChannels(event)
                            showMatches = true
                        },
                    )
                }
            }
            item(key = "sports-section") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Live & upcoming",
                        style = LocalMatchdayType.current.title.copy(fontSize = 20.sp),
                        color = MatchdayColors.Ivory,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${visibleEvents.count { isLive(it) }} live · ${visibleEvents.size - visibleEvents.count { isLive(it) }} upcoming",
                        style = LocalMatchdayType.current.meta,
                        color = MatchdayColors.Muted,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = (if (state.fromCache) "Saved schedule · " else "Schedule only · no scores · ") + state.attribution.ifBlank { "ESPN" },
                        style = LocalMatchdayType.current.meta,
                        color = MatchdayColors.Dim,
                    )
                }
            }
            grouped.forEach { (league, events) ->
                val collapsed = league in collapsedSnapshot
                item(key = "league:$league") {
                    LeagueHeader(
                        label = league,
                        count = events.size,
                        collapsed = collapsed,
                        onToggle = {
                            android.util.Log.i("MatchdaySports", "toggle league=$league collapsed=$collapsed")
                            collapsedLeagues = if (collapsed) collapsedLeagues - league else collapsedLeagues + league
                        },
                    )
                }
                if (!collapsed) {
                    items(events.chunked(3), key = { row -> row.joinToString("|") { it.id } }) { rowEvents ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowEvents.forEach { event ->
                                EventCard(
                                    event = event,
                                    selected = event.id == selected?.id,
                                    onFocused = { selectedId = event.id },
                                    onClick = {
                                        selectedId = event.id
                                        vm.findChannels(event)
                                        showMatches = true
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - rowEvents.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }

    if (showMatches && selected != null) {
        val matches = state.matches[selected.id].orEmpty()
        MatchDialog(
            event = selected,
            matches = matches,
            loading = state.matchingEventId == selected.id,
            error = state.matchError,
            onDismiss = { showMatches = false },
            onPlay = { chosen ->
                showMatches = false
                onPlay(
                    listOf(chosen) + matches
                        .filterNot { it.channel.id == chosen.channel.id }
                        .map { it.playable() },
                )
            },
        )
    }
}

@Composable
private fun SportsHeader(
    count: Int,
    liveCount: Int,
    attribution: String,
    onRefresh: () -> Unit,
    onOpenChannels: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Sports Hub", style = LocalMatchdayType.current.title.copy(fontSize = 30.sp), color = MatchdayColors.Ivory)
        Spacer(Modifier.width(30.dp))
        HeaderTab("Scores", selected = true)
        Spacer(Modifier.width(22.dp))
        HeaderTab("Channels", selected = false, onClick = onOpenChannels)
        Spacer(Modifier.weight(1f))
        Text(
            text = "$liveCount live · $count scheduled",
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

@Composable
private fun HeaderTab(label: String, selected: Boolean, onClick: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = LocalMatchdayType.current.label.copy(fontSize = 12.sp, letterSpacing = 1.4.sp),
            color = if (focused) MatchdayColors.Brick else if (selected) MatchdayColors.Ivory else MatchdayColors.Muted,
        )
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(if (selected) 54.dp else 0.dp)
                .height(2.dp)
                .background(if (selected) MatchdayColors.Brick else Color.Transparent),
        )
    }
}

@Composable
private fun SportTabs(
    sports: List<SportRegistryEntry>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val entries = remember(sports) {
        listOf(SportRegistryEntry("all", "All", "other", -1, emptyList(), dev.matchday.domain.schedule.ParticipantStyle.EVENT)) + sports
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(entries, key = { it.id }) { sport ->
            SportChip(
                label = sport.displayName,
                iconKey = sport.iconKey,
                selected = sport.id == selected,
                onClick = { onSelect(sport.id) },
            )
        }
    }
}

@Composable
private fun SportChip(label: String, iconKey: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else false
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
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportIcon(
                sport = iconKey,
                modifier = Modifier.width(14.dp).height(14.dp),
                tint = when {
                    focused -> MatchdayColors.Ink
                    selected -> MatchdayColors.Ivory
                    else -> MatchdayColors.Muted
                },
            )
            Spacer(Modifier.width(6.dp))
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
}

@Composable
private fun LeagueFilterRow(
    leagues: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        item(key = "league-all") {
            LeagueChip("All leagues", selected == null) { onSelect(null) }
        }
        items(leagues, key = { it }) { league ->
            LeagueChip(league, selected == league) { onSelect(league) }
        }
    }
}

@Composable
private fun LeagueChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else false
            }
            .background(
                when {
                    focused -> MatchdayColors.Ivory
                    selected -> MatchdayColors.SurfaceRaised
                    else -> Color.Transparent
                },
                RoundedCornerShape(50),
            )
            .border(
                if (selected && !focused) 1.dp else 0.dp,
                if (selected && !focused) MatchdayColors.CardLine else Color.Transparent,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
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
private fun LeagueHeader(
    label: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    FocusableSurface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (collapsed) "▸" else "▾",
                style = LocalMatchdayType.current.meta.copy(fontSize = 13.sp),
                color = MatchdayColors.Brick,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label.uppercase(),
                style = LocalMatchdayType.current.label.copy(fontSize = 11.sp, letterSpacing = 1.1.sp),
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

@Composable
private fun HeadlineRow(
    events: List<ScheduleEvent>,
    selectedId: String?,
    onFocus: (ScheduleEvent) -> Unit,
    onOpen: (ScheduleEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(168.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        events.take(3).forEach { event ->
            HeadlineCard(
                event = event,
                selected = event.id == selectedId,
                onFocus = { onFocus(event) },
                onClick = { onOpen(event) },
                modifier = Modifier.weight(1f),
            )
        }
        repeat((3 - events.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun HeadlineCard(
    event: ScheduleEvent,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        onFocused = onFocus,
        selected = selected,
        modifier = modifier.height(168.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = eventLabel(event).uppercase(),
                    style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                    color = MatchdayColors.Brick,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isLive(event)) {
                    MetaChip("LIVE", accent = true)
                } else {
                    Text(
                        text = dateTime(event.startMs),
                        style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                        color = MatchdayColors.Muted,
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            if (event.participants.size >= 2) {
                event.participants.take(2).forEach { participant ->
                    CompactParticipant(participant)
                    Spacer(Modifier.height(4.dp))
                }
            } else {
                Text(
                    text = displayTitle(event),
                    style = LocalMatchdayType.current.title.copy(fontSize = 18.sp),
                    color = MatchdayColors.Ivory,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.venue ?: event.broadcaster ?: "Schedule only",
                    style = LocalMatchdayType.current.meta.copy(fontSize = 10.sp),
                    color = MatchdayColors.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "CHANNELS",
                    style = LocalMatchdayType.current.label.copy(fontSize = 9.sp),
                    color = MatchdayColors.Brick,
                )
            }
        }
    }
}

@Composable
private fun EventDetail(
    event: ScheduleEvent?,
    matchCount: Int?,
    onFindChannels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MatchdayColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, MatchdayColors.CardLine, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 15.dp),
    ) {
        if (event == null) {
            Text("Focus an event to see details.", style = LocalMatchdayType.current.meta, color = MatchdayColors.Muted)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SportIcon(event.sportId, modifier = Modifier.width(17.dp).height(17.dp), tint = MatchdayColors.Muted)
            Spacer(Modifier.width(8.dp))
            MetaChip(eventLabel(event))
            Spacer(Modifier.width(8.dp))
            if (isLive(event)) MetaChip("LIVE", accent = true) else MetaChip(dateTime(event.startMs))
            Spacer(Modifier.weight(1f))
            Text(dateTime(event.startMs), style = LocalMatchdayType.current.meta, color = MatchdayColors.Muted)
        }
        Spacer(Modifier.height(13.dp))
        val teamLike = event.participants.size >= 2
        if (teamLike) {
            event.participants.take(2).forEachIndexed { index, participant ->
                ParticipantLine(participant, event.startMs)
                if (index == 0) Spacer(Modifier.height(7.dp))
            }
        } else {
            Text(
                text = event.title,
                style = LocalMatchdayType.current.title.copy(fontSize = 24.sp),
                color = MatchdayColors.Ivory,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MatchdayColors.CardLine))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = listOfNotNull(
                    event.venue,
                    event.broadcaster,
                    event.participants.joinToString(", ") { it.name }.takeIf { !teamLike },
                ).joinToString("  ·  ").ifBlank { "Schedule only" },
                style = LocalMatchdayType.current.meta,
                color = MatchdayColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            FocusableSurface(
                onClick = onFindChannels,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = when {
                        matchCount == null -> "Find channels"
                        matchCount == 0 -> "No channels"
                        else -> "$matchCount channels"
                    },
                    style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                    color = if (matchCount != null && matchCount > 0) MatchdayColors.Pitch else MatchdayColors.Brick,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ParticipantLine(participant: Participant, startMs: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RemoteBadge(
            name = participant.name,
            logoUrl = participant.logoUrl ?: participant.portraitUrl,
            size = 28.dp,
            accent = teamColor(participant.id.hashCode()),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = participant.name,
            style = LocalMatchdayType.current.subtitle.copy(fontSize = 18.sp),
            color = MatchdayColors.Ivory,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = participant.shortName ?: time(startMs),
            style = LocalMatchdayType.current.meta,
            color = MatchdayColors.Dim,
        )
    }
}

@Composable
private fun EventCard(
    event: ScheduleEvent,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableSurface(
        onClick = onClick,
        onFocused = onFocused,
        selected = selected,
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SportIcon(event.sportId, modifier = Modifier.width(14.dp).height(14.dp), tint = MatchdayColors.Dim)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = eventLabel(event),
                    style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                    color = MatchdayColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isLive(event)) MetaChip("LIVE", accent = true) else Text(
                    dateTime(event.startMs),
                    style = LocalMatchdayType.current.meta.copy(fontSize = 12.sp),
                    color = MatchdayColors.Muted,
                )
            }
            Spacer(Modifier.height(9.dp))
            if (event.participants.size >= 2) {
                event.participants.take(2).forEachIndexed { index, participant ->
                    CompactParticipant(participant)
                    if (index == 0) Spacer(Modifier.height(6.dp))
                }
            } else {
                Text(
                    text = event.title,
                    style = LocalMatchdayType.current.subtitle.copy(fontSize = 16.sp),
                    color = MatchdayColors.Ivory,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MatchdayColors.CardLine))
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.broadcaster ?: event.venue ?: eventLabel(event),
                    style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                    color = MatchdayColors.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = dateTime(event.startMs),
                    style = LocalMatchdayType.current.label.copy(fontSize = 10.sp),
                    color = MatchdayColors.Pitch,
                )
            }
        }
    }
}

@Composable
private fun CompactParticipant(participant: Participant) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RemoteBadge(
            name = participant.name,
            logoUrl = participant.logoUrl ?: participant.portraitUrl,
            size = 22.dp,
            accent = teamColor(participant.id.hashCode()),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = participant.name,
            style = LocalMatchdayType.current.subtitle.copy(fontSize = 15.sp),
            color = MatchdayColors.Ivory,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun displayTitle(event: ScheduleEvent): String =
    if (event.participants.size >= 2) {
        event.participants.take(2).joinToString("  vs  ") { it.name }
    } else {
        event.title
    }

private fun eventSubtitle(event: ScheduleEvent): String = listOfNotNull(
    eventLabel(event),
    dateTime(event.startMs),
    event.broadcaster,
).joinToString(" · ")

private fun sportRank(sportId: String): Int =
    SportRegistry.all.indexOfFirst { it.id == sportId }.let { if (it < 0) 99 else it }

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

private fun dateTime(ms: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val prefix = if (date == LocalDate.now(zone)) "" else date.format(DateTimeFormatter.ofPattern("EEE d MMM")) + " · "
    return prefix + time(ms)
}

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
    matches: List<dev.matchday.tv.data.MatchedChannel>,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPlay: (PlayableChannel) -> Unit,
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
        LaunchedEffect(matches, loading) {
            if (!loading && matches.isNotEmpty()) {
                kotlinx.coroutines.delay(100)
                runCatching { first.requestFocus() }
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
                    text = "${time(event.startMs)}  ·  ${event.broadcaster ?: "No broadcaster listed"}  ·  ${matches.size} candidate channels",
                    style = LocalMatchdayType.current.meta,
                    color = MatchdayColors.Muted,
                )
                Spacer(Modifier.height(16.dp))
                when {
                    loading -> Text("Matching your channels…", style = LocalMatchdayType.current.body, color = MatchdayColors.Muted)
                    error != null -> Text("Match failed: $error", style = LocalMatchdayType.current.body, color = MatchdayColors.Brick)
                    matches.isEmpty() -> Text(
                        "No channel matched this event above the display threshold. The guide and channel names are the only signals; Matchday will not guess a random stream.",
                        style = LocalMatchdayType.current.body,
                        color = MatchdayColors.Muted,
                        modifier = Modifier.width(800.dp),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(matches, key = { it.channel.id }) { match ->
                            FocusableSurface(
                                onClick = { onPlay(match.playable()) },
                                onFocused = {},
                                modifier = if (match == matches.first()) Modifier.focusRequester(first) else Modifier,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RemoteBadge(match.channel.name, match.channel.logoUrl, 30.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = match.channel.name,
                                            style = LocalMatchdayType.current.subtitle.copy(fontSize = 16.sp),
                                            color = MatchdayColors.Ivory,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = match.match.reasons.joinToString(" · "),
                                            style = LocalMatchdayType.current.meta.copy(fontSize = 11.sp),
                                            color = MatchdayColors.Dim,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    match.source?.let { MetaChip(it.name) }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${(match.match.confidence * 100).toInt()}%",
                                        style = LocalMatchdayType.current.label.copy(fontSize = 11.sp),
                                        color = if (match.match.autoPlayEligible) MatchdayColors.Pitch else MatchdayColors.Muted,
                                    )
                                    if (match.match.autoPlayEligible) {
                                        Spacer(Modifier.width(8.dp))
                                        MetaChip("AUTO", accent = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
