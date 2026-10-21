package dev.matchday.domain.catalog

import java.util.Locale

/**
 * Turns raw provider entries into logical channels with variants.
 *
 * v1 deliberately favours precision over recall: exact provider ids, exact normalised names,
 * and source awareness. Ambiguous cross-source merges are kept but marked low-confidence for
 * the debug view instead of being silently trusted.
 */
object CatalogBuilder {

    private const val LOW_CONFIDENCE = 0.85

    fun build(
        rawChannels: List<RawChannel>,
        overrides: Map<Long, ChannelOverride> = emptyMap(),
    ): CatalogResult {
        if (rawChannels.isEmpty()) {
            return CatalogResult(emptyList(), emptyList(), emptyList(), emptyList(), CatalogStats(0, 0, 0, 0, 0))
        }

        val prepared = rawChannels.map { raw -> prepare(raw, overrides[raw.id]) }
        val groups = prepared.groupBy { it.mergeKey }

        val channels = groups.values.map { group -> mergeGroup(group) }
            .sortedWith(compareBy<LogicalChannel>({ it.language?.code ?: "ZZ" }, { genreRank(it.genre.id) }, { it.name.lowercase(Locale.ROOT) }))

        val merges = channels.filter { it.variants.size > 1 }.map {
            MergeDecision(
                logicalKey = it.key,
                displayName = it.name,
                rawChannelIds = it.variants.map(ChannelVariant::rawChannelId),
                confidence = it.confidence,
                reason = it.mergeReason,
            )
        }
        val low = channels.filter { it.variants.size > 1 && it.confidence < LOW_CONFIDENCE }
        val unsorted = channels.filter { it.unsorted }

        return CatalogResult(
            channels = channels,
            merges = merges,
            lowConfidence = low,
            unsorted = unsorted,
            stats = CatalogStats(
                rawCount = rawChannels.size,
                logicalCount = channels.size,
                mergedCount = merges.size,
                unsortedCount = unsorted.size,
                lowConfidenceCount = low.size,
            ),
        )
    }

    private data class Prepared(
        val raw: RawChannel,
        val displayName: String,
        val normalizedName: String,
        val quality: ParsedQuality,
        val language: Language?,
        val country: String?,
        val genre: Genre,
        val sportId: String?,
        val mergeKey: String,
        val confidence: Double,
        val reason: String,
    )

    private fun prepare(raw: RawChannel, override: ChannelOverride?): Prepared {
        val normalized = NameNormalizer.normalize(raw.name)
        val tvgNormalized = raw.tvgName?.let(NameNormalizer::normalize)?.takeIf { it.isNotBlank() }
        val id = raw.tvgId?.trim()?.takeIf { it.isNotBlank() }
        val country = raw.tvgCountry?.trim()?.takeIf { it.isNotBlank() } ?: countryPrefix(raw.name)

        val language = override?.languageCode?.let(LanguageCatalog::byCodeOrNull)
            ?: LanguageCatalog.detect(raw.name, raw.tvgLanguage, country)
        val genre = GenreCatalog.classify(raw.name, raw.groupTitle, override?.genreId)
        val sport = override?.sportId ?: SportHintCatalog.detect(raw.name, raw.groupTitle)?.id
        val display = raw.tvgName?.takeIf { it.isNotBlank() }?.let(NameNormalizer::display)
            ?: NameNormalizer.display(raw.name)

        val key: String
        val confidence: Double
        val reason: String
        when {
            id != null -> {
                key = "id:$id"
                confidence = 1.0
                reason = "Exact provider / EPG id"
            }
            tvgNormalized != null && normalized.isNotBlank() && tvgNormalized == normalized -> {
                key = "name:$normalized"
                confidence = 0.95
                reason = "Normalised name + tvg-name"
            }
            else -> {
                key = "name:$normalized"
                confidence = 0.90
                reason = "Normalised name"
            }
        }

        return Prepared(
            raw = raw,
            displayName = display.ifBlank { raw.name.trim() },
            normalizedName = normalized,
            quality = NameNormalizer.quality(raw.name),
            language = language,
            country = country,
            genre = genre,
            sportId = sport,
            mergeKey = key,
            confidence = confidence,
            reason = reason,
        )
    }

    private fun mergeGroup(group: List<Prepared>): LogicalChannel {
        val first = group.first()
        val sourceCount = group.map { it.raw.sourceId }.distinct().size
        val confidence = when {
            first.mergeKey.startsWith("id:") -> 1.0
            group.size == 1 -> 1.0
            sourceCount == 1 -> maxOf(0.92, group.maxOf { it.confidence })
            else -> 0.82
        }
        val reason = when {
            first.mergeKey.startsWith("id:") -> "Exact provider / EPG id"
            group.size == 1 -> "Single raw entry"
            sourceCount == 1 -> "Same normalised name in one source"
            else -> "Same normalised name across sources"
        }
        val displayName = group.map { it.displayName }.minByOrNull { it.length } ?: first.displayName
        val candidates = group.map { it.language }.filterNotNull()
        val language = candidates.groupingBy { it.code }.eachCount().maxByOrNull { it.value }?.key?.let(LanguageCatalog::byCodeOrNull)
            ?: candidates.firstOrNull()
        val genre = group.map { it.genre }.firstOrNull { it.id != GenreCatalog.unsorted.id } ?: GenreCatalog.unsorted
        val sport = group.mapNotNull { it.sportId }.firstOrNull()
        val variants = group.map { p ->
            ChannelVariant(
                rawChannelId = p.raw.id,
                sourceId = p.raw.sourceId,
                name = p.raw.name,
                streamUrl = p.raw.streamUrl,
                quality = p.quality.tier,
                codec = p.quality.codec,
                tags = p.quality.tags,
            )
        }.sortedWith(compareByDescending<ChannelVariant> { it.quality.rank }.thenBy { it.sourceId })

        return LogicalChannel(
            key = first.mergeKey,
            name = displayName,
            normalizedName = first.normalizedName,
            language = language,
            country = first.country,
            genre = genre,
            sportId = sport,
            variants = variants,
            confidence = confidence,
            mergeReason = reason,
            unsorted = genre.id == GenreCatalog.unsorted.id,
        )
    }

    private fun countryPrefix(name: String): String? {
        val match = Regex("""^\s*\|?([A-Z]{2,4})\|?\s*[:|\-]""").find(name.uppercase(Locale.ROOT)) ?: return null
        return match.groupValues.getOrNull(1)
    }

    private fun genreRank(id: String): Int = GenreCatalog.all.indexOfFirst { it.id == id }.let { if (it < 0) 99 else it }
}
