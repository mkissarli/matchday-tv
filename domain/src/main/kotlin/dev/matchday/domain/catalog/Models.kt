package dev.matchday.domain.catalog

/** Quality tiers, highest wins when a logical channel is ranked. */
enum class QualityTier(val rank: Int, val label: String) {
    UNKNOWN(0, ""),
    SD(1, "SD"),
    HD(2, "HD"),
    FHD(3, "FHD"),
    UHD(4, "4K"),
    EIGHT_K(5, "8K"),
}

data class ParsedQuality(
    val tier: QualityTier = QualityTier.UNKNOWN,
    val codec: String? = null,
    val tags: Set<String> = emptySet(),
)

/** One raw entry as it arrived from an M3U or Xtream source. */
data class RawChannel(
    val id: Long,
    val sourceId: Long,
    val name: String,
    val streamUrl: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLanguage: String? = null,
    val tvgCountry: String? = null,
    val groupTitle: String? = null,
    val logoUrl: String? = null,
)

/** One playable stream under a logical channel. */
data class ChannelVariant(
    val rawChannelId: Long,
    val sourceId: Long,
    val name: String,
    val streamUrl: String,
    val quality: QualityTier,
    val codec: String?,
    val tags: Set<String>,
)

data class Genre(val id: String, val label: String)

data class Language(val code: String, val label: String)

data class ChannelOverride(
    val languageCode: String? = null,
    val genreId: String? = null,
    val sportId: String? = null,
)

data class LogicalChannel(
    val key: String,
    val name: String,
    val normalizedName: String,
    val language: Language?,
    val country: String?,
    val genre: Genre,
    val sportId: String?,
    val variants: List<ChannelVariant>,
    val confidence: Double,
    val mergeReason: String,
    val unsorted: Boolean,
)

data class MergeDecision(
    val logicalKey: String,
    val displayName: String,
    val rawChannelIds: List<Long>,
    val confidence: Double,
    val reason: String,
)

data class CatalogStats(
    val rawCount: Int,
    val logicalCount: Int,
    val mergedCount: Int,
    val unsortedCount: Int,
    val lowConfidenceCount: Int,
)

data class CatalogResult(
    val channels: List<LogicalChannel>,
    val merges: List<MergeDecision>,
    val lowConfidence: List<LogicalChannel>,
    val unsorted: List<LogicalChannel>,
    val stats: CatalogStats,
)
