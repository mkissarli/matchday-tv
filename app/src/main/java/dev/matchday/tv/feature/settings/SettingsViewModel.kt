package dev.matchday.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matchday.domain.catalog.QualityTier
import dev.matchday.tv.data.PlaybackPreferences
import dev.matchday.tv.data.ScheduleCache
import dev.matchday.tv.setup.SetupInfo
import dev.matchday.tv.setup.SetupPayload
import dev.matchday.tv.setup.SetupServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.work.EpgSyncScheduler

data class SettingsUiState(
    val setup: SetupInfo? = null,
    val setupError: String? = null,
    val importing: Boolean = false,
    val progress: String? = null,
    val message: String? = null,
    val error: String? = null,
    val sources: List<SourceEntity> = emptyList(),
    val maxTier: QualityTier = QualityTier.UHD,
)

class SettingsViewModel(
    private val importer: SourceImporter,
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val playbackPreferences: PlaybackPreferences,
    private val scheduleCache: ScheduleCache,
    private val epgSources: EpgSourceStore,
    private val epgSyncScheduler: EpgSyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(maxTier = playbackPreferences.maxTier()),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val setupServer = SetupServer()
    private var profileId: Long = -1L

    init {
        viewModelScope.launch {
            importer.progress.collect { stage ->
                _state.update { it.copy(progress = stage?.toString()) }
            }
        }
        viewModelScope.launch { refreshSources() }
    }

    fun startPhoneSetup() {
        if (setupServer.running) return
        val info = setupServer.start { payload -> importFromPhone(payload) }
        _state.update {
            it.copy(
                setup = info,
                setupError = if (info == null) "Could not open the setup port on this TV." else null,
            )
        }
    }

    fun stopPhoneSetup() {
        setupServer.stop()
        _state.update { it.copy(setup = null) }
    }

    fun importManual(name: String, url: String, epgUrl: String) {
        importSource(SetupPayload(name, url, epgUrl))
    }

    fun setMaxTier(tier: QualityTier) {
        playbackPreferences.setMaxTier(tier)
        _state.update { it.copy(maxTier = tier) }
    }

    fun clearScheduleCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { scheduleCache.save(emptyList()) }
            _state.update { it.copy(message = "Saved schedule cleared.") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null) }

    private fun importFromPhone(payload: SetupPayload) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    importing = true,
                    message = "Receiving ${payload.name}…",
                    error = null,
                )
            }
            val result = runCatching { performImport(payload) }
            result.onSuccess { count ->
                _state.update {
                    it.copy(
                        importing = false,
                        progress = null,
                        message = "Imported ${payload.name} · $count channels",
                        error = null,
                    )
                }
                refreshSources()
            }.onFailure { failure ->
                _state.update {
                    it.copy(
                        importing = false,
                        progress = null,
                        message = null,
                        error = failure.message ?: failure.javaClass.simpleName,
                    )
                }
            }
        }
    }

    private fun importSource(payload: SetupPayload) {
        if (payload.playlistUrl.isBlank()) {
            _state.update { it.copy(error = "Paste an M3U or Xtream URL first.") }
            return
        }
        importFromPhone(payload)
    }

    private suspend fun performImport(payload: SetupPayload): Int {
        val pid = activeProfile()
        importer.useProfile(pid)
        importer.m3u(
            name = payload.name.ifBlank { "Playlist" },
            url = payload.playlistUrl.trim(),
            epgUrl = payload.epgUrl.orEmpty().trim(),
            makeDefault = true,
        )
        val guideUrl = payload.epgUrl?.trim().orEmpty()
        if (guideUrl.isNotBlank()) {
            val guide = epgSources.ensure("${payload.name} guide", guideUrl)
            epgSyncScheduler.enqueueSync(guide.id, "matchday-setup")
        }
        return when (val result = importer.state.value) {
            is SourceImporter.ImportState.Success -> result.counts?.channels ?: 0
            is SourceImporter.ImportState.Failed -> throw IllegalStateException(friendlyError(result))
            else -> 0
        }
    }

    private suspend fun refreshSources() {
        val pid = activeProfile()
        val ids = sourceDao.sourceIdsForProfile(pid)
        val sources = ids.mapNotNull { sourceDao.getById(it) }
        _state.update { it.copy(sources = sources) }
    }

    private suspend fun activeProfile(): Long {
        if (profileId >= 0L) return profileId
        val current = settings.activeProfileIdNow()
        if (current >= 0L) return current.also { profileId = it }
        val id = importer.createProfile("Viewer", 0, isKids = false, pin = null)
        settings.setActiveProfile(id)
        profileId = id
        return id
    }

    private fun friendlyError(failure: SourceImporter.ImportState.Failed): String = when (val f = failure.failure) {
        is SourceImporter.SetupFailure.Sync -> f.failure.toString()
        SourceImporter.SetupFailure.BackupRead -> "The backup could not be read."
        SourceImporter.SetupFailure.Restore -> "The backup could not be restored."
        SourceImporter.SetupFailure.InvalidMac -> "That portal address is invalid."
    }

    override fun onCleared() {
        setupServer.stop()
    }
}
