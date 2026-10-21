package dev.matchday.tv.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matchday.domain.catalog.CatalogBuilder
import dev.matchday.domain.catalog.LogicalChannel
import dev.matchday.domain.catalog.RawChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.setup.SourceImporter

data class PlayableChannel(
    val channel: ChannelEntity,
    val source: SourceEntity?,
    val categoryName: String?,
    val logical: LogicalChannel? = null,
    val languageCode: String? = null,
)

data class BrowseCategory(
    val key: String,
    val label: String,
    val count: Int,
)

data class NowNext(
    val nowTitle: String?,
    val nowStartMs: Long?,
    val nowStopMs: Long?,
    val nextTitle: String?,
    val nextStartMs: Long?,
)

data class LiveUiState(
    val loading: Boolean = true,
    val importing: Boolean = false,
    val progress: String? = null,
    val error: String? = null,
    val channels: List<PlayableChannel> = emptyList(),
    val categories: List<BrowseCategory> = emptyList(),
    val favoriteRawIds: Set<Long> = emptySet(),
    val recentRawIds: Set<Long> = emptySet(),
    val nowNext: Map<Long, NowNext> = emptyMap(),
    val rawCount: Int = 0,
    val logicalCount: Int = 0,
    val sourceCount: Int = 0,
)

class LiveViewModel(
    private val importer: SourceImporter,
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val epgDao: EpgDao,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()
    private var profileId: Long = -1

    init {
        viewModelScope.launch {
            importer.progress.collect { stage ->
                _state.update { it.copy(progress = stage?.toString()) }
            }
        }
        viewModelScope.launch {
            importer.state.drop(1).collect { state ->
                if (state is SourceImporter.ImportState.Success) refresh()
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
            val id = activeProfile()
            importer.useProfile(id)
            importer.m3u(name = name.ifBlank { "Playlist" }, url = url.trim(), makeDefault = true)
            when (val result = importer.state.value) {
                is SourceImporter.ImportState.Success -> refresh()
                is SourceImporter.ImportState.Failed -> _state.update {
                    it.copy(importing = false, progress = null, error = friendlyError(result))
                }
                else -> Unit
            }
            _state.update { it.copy(importing = false, progress = null) }
        }
    }

    fun markWatched(playable: PlayableChannel) {
        viewModelScope.launch {
            val pid = activeProfile()
            runCatching {
                historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = playable.channel.id))
            }
            _state.update {
                it.copy(
                    recentRawIds = it.recentRawIds + playable.logical?.variants?.map { v -> v.rawChannelId }.orEmpty() + playable.channel.id,
                    categories = buildCategories(it.channels, it.favoriteRawIds, it.recentRawIds + playable.channel.id),
                )
            }
        }
    }

    fun toggleFavorite(playable: PlayableChannel) {
        viewModelScope.launch {
            val ids = playable.logical?.variants?.map { it.rawChannelId } ?: listOf(playable.channel.id)
            val pid = activeProfile()
            if (ids.any { it in _state.value.favoriteRawIds }) {
                ids.forEach { favoriteDao.remove(pid, MediaType.LIVE, it) }
            } else {
                favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = playable.channel.id))
            }
            refresh()
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private suspend fun refresh() {
        _state.update { it.copy(loading = true) }
        val pid = activeProfile()
        val sourceIds = sourceDao.sourceIdsForProfile(pid)
        val sourceEntities = sourceIds.mapNotNull { sourceDao.getById(it) }
        val sourceById = sourceEntities.associateBy { it.id }
        val rawEntities = if (sourceIds.isEmpty()) emptyList() else channelDao.snapshotAll(sourceIds, CHANNEL_SNAPSHOT_LIMIT)
        val categoryIds = rawEntities.mapNotNull { it.categoryId }.distinct()
        val categoryNames = categoryIds.associateWith { id -> categoryDao.getById(id)?.name }
        val rawChannels = rawEntities.map { channel ->
            RawChannel(
                id = channel.id,
                sourceId = channel.sourceId,
                name = channel.name,
                streamUrl = channel.streamUrl,
                tvgId = channel.epgChannelId,
                groupTitle = channel.categoryId?.let(categoryNames::get),
                logoUrl = channel.logoUrl,
            )
        }
        val catalog = withContext(Dispatchers.Default) { CatalogBuilder.build(rawChannels) }
        val rawById = rawEntities.associateBy { it.id }
        val playables = catalog.channels.mapNotNull { logical ->
            val rawId = logical.variants.firstOrNull()?.rawChannelId ?: return@mapNotNull null
            val raw = rawById[rawId] ?: return@mapNotNull null
            PlayableChannel(
                channel = raw.copy(name = logical.name),
                source = sourceById[raw.sourceId],
                categoryName = "${logical.language?.label ?: "Unknown"} → ${logical.genre.label}",
                logical = logical,
                languageCode = logical.language?.code,
            )
        }
        val favoriteIds = favoriteDao.observeFavoriteIds(pid, MediaType.LIVE).first().toSet()
        val recentIds = channelDao.recentlyWatched(pid, 64).first().map { it.id }.toSet()
        val now = System.currentTimeMillis()
        val nowNext = buildMap {
            playables.take(120).forEach { playable ->
                val epgId = playable.channel.epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@forEach
                val nowProg = runCatching { epgDao.nowPlaying(epgId, now) }.getOrNull()
                val nextProg = runCatching {
                    epgDao.upcoming(epgId, now, 12).first()
                        .firstOrNull { it.startMs > (nowProg?.startMs ?: 0) }
                }.getOrNull()
                if (nowProg != null || nextProg != null) {
                    put(
                        playable.channel.id,
                        NowNext(
                            nowTitle = nowProg?.title,
                            nowStartMs = nowProg?.startMs,
                            nowStopMs = nowProg?.stopMs,
                            nextTitle = nextProg?.title,
                            nextStartMs = nextProg?.startMs,
                        ),
                    )
                }
            }
        }
        val categories = buildCategories(playables, favoriteIds, recentIds)
        _state.update {
            it.copy(
                loading = false,
                channels = playables,
                categories = categories,
                favoriteRawIds = favoriteIds,
                recentRawIds = recentIds,
                nowNext = nowNext,
                rawCount = rawEntities.size,
                logicalCount = playables.size,
                sourceCount = sourceEntities.size,
                error = null,
            )
        }
    }

    private fun buildCategories(
        channels: List<PlayableChannel>,
        favoriteIds: Set<Long>,
        recentIds: Set<Long>,
    ): List<BrowseCategory> {
        val favorites = channels.count { channel -> channel.logical?.variants?.any { it.rawChannelId in favoriteIds } == true }
        val recents = channels.count { channel -> channel.logical?.variants?.any { it.rawChannelId in recentIds } == true }
        val groups = channels.groupBy { it.categoryName ?: "Unsorted" }
            .map { (label, list) -> BrowseCategory("cat:$label", label, list.size) }
            .sortedBy { it.label }
        return buildList {
            add(BrowseCategory("all", "All channels", channels.size))
            add(BrowseCategory("favorites", "Favorites", favorites))
            add(BrowseCategory("recent", "Recently watched", recents))
            addAll(groups)
        }
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

    private companion object {
        const val CHANNEL_SNAPSHOT_LIMIT = 60_000
    }
}
