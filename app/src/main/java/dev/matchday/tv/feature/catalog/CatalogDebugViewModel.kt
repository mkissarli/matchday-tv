package dev.matchday.tv.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.matchday.domain.catalog.CatalogBuilder
import dev.matchday.domain.catalog.CatalogResult
import dev.matchday.domain.catalog.RawChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.settings.SettingsRepository

data class CatalogDebugState(
    val loading: Boolean = true,
    val error: String? = null,
    val rawCount: Int = 0,
    val result: CatalogResult? = null,
)

class CatalogDebugViewModel(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogDebugState())
    val state: StateFlow<CatalogDebugState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = CatalogDebugState(loading = true)
            runCatching {
                val profileId = settings.activeProfileIdNow()
                if (profileId < 0) return@runCatching CatalogDebugState(loading = false, rawCount = 0, result = CatalogBuilder.build(emptyList()))
                val sourceIds = sourceDao.sourceIdsForProfile(profileId)
                val raw = channelDao.snapshotAll(sourceIds, 5_000)
                val categoryIds = raw.mapNotNull { it.categoryId }.distinct()
                val categoryNames = categoryIds.associateWith { id -> categoryDao.getById(id)?.name }
                val channels = raw.map { channel ->
                    RawChannel(
                        id = channel.id,
                        sourceId = channel.sourceId,
                        name = channel.name,
                        streamUrl = channel.streamUrl,
                        tvgId = channel.epgChannelId,
                        tvgName = null,
                        tvgLanguage = null,
                        tvgCountry = null,
                        groupTitle = channel.categoryId?.let(categoryNames::get),
                        logoUrl = channel.logoUrl,
                    )
                }
                CatalogDebugState(
                    loading = false,
                    rawCount = raw.size,
                    result = CatalogBuilder.build(channels),
                )
            }.onSuccess { _state.value = it }
                .onFailure { _state.value = CatalogDebugState(loading = false, error = it.message ?: it.javaClass.simpleName) }
        }
    }

    suspend fun reload() = withContext(Dispatchers.IO) { refresh() }
}
