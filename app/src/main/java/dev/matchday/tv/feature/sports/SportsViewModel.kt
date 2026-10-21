package dev.matchday.tv.feature.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery
import dev.matchday.domain.schedule.ScheduleStatus
import dev.matchday.domain.schedule.SportRegistry
import dev.matchday.domain.schedule.SportRegistryEntry
import dev.matchday.tv.data.BroadcastLookup
import dev.matchday.tv.data.MatchRepository
import dev.matchday.tv.data.MatchedChannel
import dev.matchday.tv.data.ScheduleCache
import dev.matchday.tv.data.SuggestedRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SportsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val events: List<ScheduleEvent> = emptyList(),
    val selectedSportId: String = "all",
    val sports: List<SportRegistryEntry> = SportRegistry.all,
    val attribution: String = "",
    val updatedAtMs: Long = 0,
    val matchingEventId: String? = null,
    val matches: Map<String, List<MatchedChannel>> = emptyMap(),
    val matchError: String? = null,
    val fromCache: Boolean = false,
    // Per-fixture broadcaster suggestions (LiveSoccerTV service lookup).
    val lookupEventId: String? = null,
    val listings: Map<String, List<SuggestedRow>> = emptyMap(),
    val lookupFailures: Map<String, String> = emptyMap(),
)

class SportsViewModel(
    private val provider: FixtureProvider,
    private val matcher: MatchRepository,
    private val cache: ScheduleCache,
    private val broadcastLookup: BroadcastLookup,
) : ViewModel() {

    private val _state = MutableStateFlow(SportsUiState(attribution = provider.attribution))
    val state: StateFlow<SportsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { cache.load() }
            val now = System.currentTimeMillis()
            val usable = cached.events.filter {
                (it.endMs ?: it.startMs) >= now - 2 * 60 * 60_000L &&
                    it.startMs <= now + 7 * 24 * 60 * 60_000L
            }
            if (usable.isNotEmpty() && _state.value.events.isEmpty()) {
                _state.update {
                    it.copy(
                        events = usable,
                        sports = visibleSports(usable),
                        updatedAtMs = cached.savedAtMs,
                        fromCache = true,
                        loading = false,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val now = System.currentTimeMillis()
                val query = ScheduleQuery(
                    fromMs = now - 2 * 60 * 60_000L,
                    toMs = now + 7 * 24 * 60 * 60_000L,
                )
                provider.events(query)
                    .filter { it.status != ScheduleStatus.CANCELLED }
                    .sortedBy { it.startMs }
            }.onSuccess { events ->
                val now = System.currentTimeMillis()
                val previous = _state.value.events
                // A partial refresh (one free provider rate-limited) must not erase a whole
                // sport we already had. Keep cached events for sports missing from this run.
                val freshSports = events.map { it.sportId }.toSet()
                val preserve = previous.filter { event ->
                    event.sportId !in freshSports &&
                        (event.endMs ?: event.startMs) >= now - 2 * 60 * 60_000L &&
                        event.startMs <= now + 7 * 24 * 60 * 60_000L
                }
                val merged = (events + preserve)
                    .distinctBy { it.id }
                    .sortedBy { it.startMs }
                if (merged.isNotEmpty()) {
                    withContext(Dispatchers.IO) { cache.save(merged) }
                }
                _state.update {
                    val keepCached = merged.isEmpty() && it.events.isNotEmpty()
                    val visible = if (keepCached) it.events else merged
                    it.copy(
                        loading = false,
                        error = null,
                        events = visible,
                        sports = visibleSports(visible),
                        attribution = provider.attribution,
                        updatedAtMs = if (keepCached) it.updatedAtMs else now,
                        fromCache = keepCached,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    val hasCached = it.events.isNotEmpty()
                    it.copy(
                        loading = false,
                        error = if (hasCached) null else (error.message ?: error.javaClass.simpleName),
                        fromCache = hasCached,
                    )
                }
            }
        }
    }

    fun selectSport(id: String) = _state.update { it.copy(selectedSportId = id) }

    private fun visibleSports(events: List<ScheduleEvent>): List<SportRegistryEntry> {
        val ids = events.map { it.sportId }.toSet()
        return SportRegistry.all.filter {
            it.id in ids || SportRegistry.isLaunchSport(it.id) || it.id == "other"
        }
    }

    fun findChannels(event: ScheduleEvent) {
        viewModelScope.launch {
            _state.update { it.copy(matchingEventId = event.id, matchError = null) }
            runCatching { matcher.find(event) }
                .onSuccess { matches ->
                    _state.update { it.copy(matchingEventId = null, matches = it.matches + (event.id to matches)) }
                }
                .onFailure { error ->
                    _state.update { it.copy(matchingEventId = null, matchError = error.message ?: error.javaClass.simpleName) }
                }
        }
    }

    /**
     * Opens the match channels sheet: broadcaster suggestions first (fast service
     * lookup, ranked UK -> US -> FR -> Arabic -> DE), with the guide/EPG deep
     * matcher as the final hope when the service has nothing for the fixture.
     */
    fun openMatch(event: ScheduleEvent) {
        if (event.sportId == "football") {
            if (event.id in _state.value.listings && _state.value.listings[event.id] != null) return
            if (_state.value.lookupEventId == event.id) return
            viewModelScope.launch { lookupBroadcasts(event) }
        } else {
            // The broadcaster service covers football only.
            if (event.id !in _state.value.matches) findChannels(event)
        }
    }

    private suspend fun lookupBroadcasts(event: ScheduleEvent) {
        _state.update { it.copy(lookupEventId = event.id) }
        val listing = runCatching { broadcastLookup.lookup(event) }.getOrNull()
        if (listing == null) {
            _state.update {
                it.copy(
                    lookupEventId = null,
                    lookupFailures = it.lookupFailures + (event.id to "no broadcaster listing"),
                )
            }
            // Final hope: the deep guide matcher.
            if (event.id !in _state.value.matches && _state.value.matchingEventId == null) {
                findChannels(event)
            }
            return
        }
        val rows = runCatching { matcher.enrich(listing) }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                lookupEventId = null,
                listings = it.listings + (event.id to rows),
            )
        }
    }
}
