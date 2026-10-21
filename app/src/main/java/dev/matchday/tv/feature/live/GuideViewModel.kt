package dev.matchday.tv.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.settings.SettingsRepository

data class GuideRow(
    val channel: ChannelEntity,
    val programmes: List<EpgProgrammeEntity>,
)

data class GuideUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val fromMs: Long = 0,
    val toMs: Long = 0,
    val rows: List<GuideRow> = emptyList(),
)

class GuideViewModel(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
) : ViewModel() {

    private val _state = MutableStateFlow(GuideUiState())
    val state: StateFlow<GuideUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = GuideUiState(loading = true)
            runCatching {
                val profileId = settings.activeProfileIdNow()
                val sourceIds = if (profileId < 0) emptyList() else sourceDao.sourceIdsForProfile(profileId)
                val channels = if (sourceIds.isEmpty()) emptyList() else channelDao.snapshotAll(sourceIds, 500)
                val now = System.currentTimeMillis()
                val from = now - 30 * 60_000L
                val to = now + 4 * 60 * 60_000L
                val rows = withContext(Dispatchers.IO) {
                    channels.take(40).map { channel ->
                        val epgId = channel.epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                        val programmes = if (epgId == null) emptyList() else runCatching {
                            epgDao.programmeSummariesForChannel(epgId, from, to)
                        }.getOrDefault(emptyList())
                        GuideRow(channel, programmes)
                    }
                }
                GuideUiState(loading = false, fromMs = from, toMs = to, rows = rows)
            }.onSuccess { _state.value = it }
                .onFailure { _state.value = GuideUiState(loading = false, error = it.message ?: it.javaClass.simpleName) }
        }
    }
}
