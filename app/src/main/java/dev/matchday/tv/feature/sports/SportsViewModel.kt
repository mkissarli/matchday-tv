package dev.matchday.tv.feature.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery
import dev.matchday.domain.schedule.ScheduleStatus
import dev.matchday.domain.schedule.SportRegistry
import dev.matchday.domain.schedule.SportRegistryEntry
import dev.matchday.tv.data.MatchRepository
import dev.matchday.tv.data.MatchedChannel
import dev.matchday.tv.data.ScheduleCache
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
)

class SportsViewModel(
    private val provider: FixtureProvider,
    private val matcher: MatchRepository,
    private val cache: ScheduleCache,
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
                if (events.isNotEmpty()) {
                    withContext(Dispatchers.IO) { cache.save(events) }
                }
                _state.update {
                    val keepCached = events.isEmpty() && it.events.isNotEmpty()
                    val visible = if (keepCached) it.events else events
                    it.copy(
                        loading = false,
                        error = null,
                        events = visible,
                        sports = visibleSports(visible),
                        attribution = provider.attribution,
                        updatedAtMs = if (keepCached) it.updatedAtMs else System.currentTimeMillis(),
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
}
