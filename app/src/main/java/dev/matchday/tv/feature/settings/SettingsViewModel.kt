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
import tv.own.owntv.core.util.FriendlySyncFailure

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
        val url = payload.playlistUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            _state.update { it.copy(error = "The playlist URL must start with http:// or https://.") }
            return
        }
        val host = url.substringAfter("://", "").substringBefore('/')
        if (host.startsWith("127.0.0.1") || host.startsWith("localhost", ignoreCase = true)) {
            _state.update {
                it.copy(
                    error = "That URL points at the device itself. On the emulator use 10.0.2.2; " +
                        "on a real TV use the PC/server LAN IP.",
                )
            }
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
            runCatching {
                val guide = epgSources.ensure("${payload.name} guide", guideUrl)
                epgSyncScheduler.enqueueSync(guide.id, "matchday-setup")
            }
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
        is SourceImporter.SetupFailure.Sync -> humanFailure(f.failure)
        SourceImporter.SetupFailure.BackupRead -> "The backup could not be read."
        SourceImporter.SetupFailure.Restore -> "The backup could not be restored."
        SourceImporter.SetupFailure.InvalidMac -> "That portal address is invalid."
    }

    private fun humanFailure(failure: FriendlySyncFailure): String = when (failure) {
        FriendlySyncFailure.Offline -> "The TV is offline. Check its network connection."
        FriendlySyncFailure.Timeout -> "The provider timed out. Try again."
        FriendlySyncFailure.Unreachable -> "The hostname could not be resolved. Check the URL."
        FriendlySyncFailure.ConnectionFailed -> "The provider refused the connection. Check the URL and port."
        FriendlySyncFailure.StreamInterrupted -> "The connection was reset while reading the playlist."
        FriendlySyncFailure.AuthenticationRejected -> "The provider rejected the login (HTTP 401/403)."
        FriendlySyncFailure.NotFound -> "The playlist URL returned HTTP 404. Check the path."
        FriendlySyncFailure.ServerError -> "The provider returned a server error. Try again later."
        FriendlySyncFailure.SecureConnectionFailed -> "The HTTPS certificate could not be verified."
        FriendlySyncFailure.MalformedGuide -> "The provider returned malformed data."
        FriendlySyncFailure.PlaylistFileUnavailable -> "That playlist file is not available."
        FriendlySyncFailure.PlaylistPathUnsupported -> "Use an http(s) playlist URL, not a local file path."
        FriendlySyncFailure.MacNotAuthorised, FriendlySyncFailure.InvalidMac -> "The portal rejected this MAC address."
        FriendlySyncFailure.PortalHandshakeFailed -> "The portal handshake failed."
        FriendlySyncFailure.PortalSessionExpired -> "The portal session expired. Try again."
        FriendlySyncFailure.GuideEmpty -> "The guide feed contained no programmes."
        FriendlySyncFailure.Generic -> "The provider returned an empty or invalid response."
        is FriendlySyncFailure.Unknown -> failure.rawMessage.ifBlank { "The import failed." }
    }

    override fun onCleared() {
        setupServer.stop()
    }
}
