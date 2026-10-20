package dev.matchday.tv.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.setup.SourceImporter

data class PlayableChannel(
    val channel: ChannelEntity,
    val source: SourceEntity?,
    val categoryName: String?,
)

data class LiveUiState(
    val loading: Boolean = true,
    val importing: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val channels: List<PlayableChannel> = emptyList(),
    val sourceCount: Int = 0,
)

class LiveViewModel(
    private val importer: SourceImporter,
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            importer.progress.collect { stage ->
                _state.update { it.copy(progress = stage?.toString()) }
            }
        }
        viewModelScope.launch { refresh() }
    }

    fun importPlaylist(name: String, url: String) {
        if (url.isBlank()) {
            _state.update { it.copy(error = "Paste an M3U URL first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null, progress = null) }
            val profileId = activeProfile()
            importer.useProfile(profileId)
            importer.m3u(
                name = name.ifBlank { "Playlist" },
                url = url.trim(),
                makeDefault = true,
            )
            when (val result = importer.state.value) {
                is SourceImporter.ImportState.Success -> {
                    refresh()
                    _state.update { it.copy(importing = false, progress = null) }
                }
                is SourceImporter.ImportState.Failed -> {
                    _state.update {
                        it.copy(
                            importing = false,
                            progress = null,
                            error = friendlyError(result),
                        )
                    }
                }
                else -> _state.update { it.copy(importing = false, progress = null) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private suspend fun refresh() {
        _state.update { it.copy(loading = true) }
        val profileId = activeProfile()
        val sourceIds = sourceDao.sourceIdsForProfile(profileId)
        val sources = sourceIds.mapNotNull { sourceDao.getById(it) }
        val byId = sources.associateBy { it.id }
        val channels = if (sourceIds.isEmpty()) {
            emptyList()
        } else {
            channelDao.snapshotAll(sourceIds, 2_000).map { channel ->
                val source = byId[channel.sourceId]
                val categoryName = channel.categoryId?.let { categoryDao.getById(it)?.name }
                PlayableChannel(channel, source, categoryName)
            }
        }
        _state.update {
            it.copy(
                loading = false,
                channels = channels,
                sourceCount = sources.size,
                error = null,
            )
        }
    }

    private suspend fun activeProfile(): Long {
        val current = settings.activeProfileIdNow()
        if (current >= 0L) return current
        val id = importer.createProfile("Viewer", 0, isKids = false, pin = null)
        settings.setActiveProfile(id)
        return id
    }

    private fun friendlyError(failure: SourceImporter.ImportState.Failed): String = when (val f = failure.failure) {
        is SourceImporter.SetupFailure.Sync -> f.failure.toString()
        SourceImporter.SetupFailure.BackupRead -> "The backup could not be read."
        SourceImporter.SetupFailure.Restore -> "The backup could not be restored."
        SourceImporter.SetupFailure.InvalidMac -> "That portal address is invalid."
    }
}
