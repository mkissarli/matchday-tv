package dev.matchday.tv.data

import dev.matchday.domain.catalog.LanguageCatalog
import dev.matchday.domain.catalog.NameNormalizer
import dev.matchday.domain.matching.ChannelCandidate
import dev.matchday.domain.matching.FixtureMatcher
import dev.matchday.domain.matching.MatchEvent
import dev.matchday.domain.matching.MatchParticipant
import dev.matchday.domain.matching.ParticipantKind
import dev.matchday.domain.matching.ProgrammeCandidate
import dev.matchday.domain.matching.StreamMatch
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.tv.feature.live.PlayableChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.settings.SettingsRepository

data class MatchedChannel(
    val channel: ChannelEntity,
    val source: SourceEntity?,
    val match: StreamMatch,
    val languageCode: String? = null,
) {
    fun playable(): PlayableChannel = PlayableChannel(
        channel = channel,
        source = source,
        categoryName = null,
        languageCode = languageCode,
    )
}

/** One broadcast channel suggestion, with the user's own channels it matches. */
data class SuggestedRow(
    val broadcast: BroadcastChannel,
    val channels: List<ChannelEntity>,
)

/**
 * Pure broadcast-channel → user-channel name matching. Lives separately from
 * [MatchRepository] so it can be unit-tested without a database.
 */
internal object ChannelScorer {

    class ChannelEntry(val channel: ChannelEntity) {
        private val normalized = NameNormalizer.normalize(channel.name)
        val compact = normalized.replace(" ", "")
        val tokens = normalized.split(' ').filter { it.isNotBlank() }.toSet()
        val digits = DIGITS.findAll(normalized).map { it.value }.toSet()
    }

    class BroadcastKey(raw: String) {
        private val normalized = NameNormalizer.normalize(raw)
        val compact = normalized.replace(" ", "")
        val tokens = normalized.split(' ').filter { it.isNotBlank() }.toSet()
        val digits = DIGITS.findAll(normalized).map { it.value }.toSet()
    }

    fun score(broadcast: BroadcastKey, entry: ChannelEntry): Int? {
        if (broadcast.compact.length < 3) return null
        // "TNT Sports 1" must never match "TNT Sports 2", but a numberless
        // "TNT Sports" copy is acceptable.
        if (broadcast.digits.isNotEmpty() && entry.digits.isNotEmpty() &&
            broadcast.digits.intersect(entry.digits).isEmpty()
        ) return null
        val contained = entry.compact.contains(broadcast.compact) ||
            (broadcast.compact.contains(entry.compact) && entry.compact.length >= 3)
        // Tokens match exactly or as a stem ("Sky Sport" ↔ "Sky Sports").
        val tokenOverlap = broadcast.tokens.count { t ->
            entry.tokens.any { u -> u == t || (t.length >= 4 && u.startsWith(t)) || (u.length >= 4 && t.startsWith(u)) }
        }
        val tokenAll = broadcast.tokens.isNotEmpty() && tokenOverlap == broadcast.tokens.size
        if (!contained && !tokenAll) return null
        return when {
            entry.compact == broadcast.compact -> 0
            contained -> 1
            else -> 2
        }
    }

    private val DIGITS = Regex("""\d+""")
}

class MatchRepository(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
) {

    /**
     * Name-search the user's playlist for each broadcast channel a listing
     * reports. Pure dictionary work: no EPG probe, no network, instant.
     */
    suspend fun enrich(
        listing: BroadcastListing,
        maxPerRow: Int = 6,
    ): List<SuggestedRow> = withContext(Dispatchers.IO) {
        val profileId = settings.activeProfileIdNow()
        if (profileId < 0) return@withContext emptyList()
        val sourceIds = sourceDao.sourceIdsForProfile(profileId)
        if (sourceIds.isEmpty()) return@withContext emptyList()
        val index = channelDao.snapshotAll(sourceIds, CHANNEL_SNAPSHOT_LIMIT)
            .map { ChannelScorer.ChannelEntry(it) }
        listing.channels.map { broadcast ->
            val key = ChannelScorer.BroadcastKey(broadcast.channelName)
            val matched = index.mapNotNull { entry ->
                ChannelScorer.score(key, entry)?.let { score -> score * SCORE_SCALE + rank(entry) to entry.channel }
            }
                .sortedBy { it.first }
                .take(maxPerRow)
                .map { it.second }
            SuggestedRow(broadcast, matched)
        }
    }

    private fun rank(entry: ChannelScorer.ChannelEntry): Int =
        NameNormalizer.quality(entry.channel.name).tier.rank * 100 + entry.compact.length

    suspend fun find(
        event: ScheduleEvent,
        preferredLanguages: List<String> = listOf("EN", "FR", "AR", "DE"),
    ): List<MatchedChannel> = withContext(Dispatchers.IO) {
        val profileId = settings.activeProfileIdNow()
        if (profileId < 0) return@withContext emptyList()
        val sourceIds = sourceDao.sourceIdsForProfile(profileId)
        if (sourceIds.isEmpty()) return@withContext emptyList()
        val sources = sourceIds.mapNotNull { sourceDao.getById(it) }.associateBy { it.id }
        val channels = channelDao.snapshotAll(sourceIds, CHANNEL_SNAPSHOT_LIMIT)
        val languages = channels.associate { channel ->
            channel.id.toString() to LanguageCatalog.detect(channel.name, null, null)?.code
        }
        val candidates = channels.map { channel ->
            ChannelCandidate(
                id = channel.id.toString(),
                rawName = channel.name,
                logicalName = channel.name,
                epgChannelId = channel.epgChannelId,
                language = languages[channel.id.toString()],
                qualityRank = NameNormalizer.quality(channel.name).tier.rank,
                sourceId = channel.sourceId,
            )
        }
        val from = event.startMs - FixtureMatcher.TIME_WINDOW_MS
        val to = event.startMs + FixtureMatcher.TIME_WINDOW_MS
        val epgIds = channels.mapNotNull { channel ->
            channel.epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        }.distinct()
        val programmeRows = runCatching {
            epgIds.chunked(400).flatMap { chunk ->
                epgDao.programmeSummariesForChannels(chunk, from, to)
            }
        }.getOrDefault(emptyList())
        val programmesByEpg = programmeRows.groupBy { it.epgChannelId }
        val programmes = ArrayList<ProgrammeCandidate>()
        channels.forEach { channel ->
            val epgId = channel.epgChannelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@forEach
            programmesByEpg[epgId].orEmpty().forEach { programme ->
                programmes += ProgrammeCandidate(
                    channelId = channel.id.toString(),
                    title = programme.title,
                    startMs = programme.startMs,
                    stopMs = programme.stopMs,
                )
            }
        }
        val matchEvent = MatchEvent(
            id = event.id,
            title = event.title,
            sportId = event.sportId,
            competitionName = event.competitionName,
            participants = event.participants.map { participant ->
                MatchParticipant(
                    name = participant.name,
                    shortName = participant.shortName,
                    aliases = buildList {
                        participant.countryCode?.let(::add)
                    },
                    kind = when (participant.kind) {
                        dev.matchday.domain.schedule.ParticipantKind.TEAM -> ParticipantKind.TEAM
                        dev.matchday.domain.schedule.ParticipantKind.ATHLETE -> ParticipantKind.ATHLETE
                        dev.matchday.domain.schedule.ParticipantKind.DRIVER -> ParticipantKind.DRIVER
                        dev.matchday.domain.schedule.ParticipantKind.FIGHTER -> ParticipantKind.PLAYER
                        else -> ParticipantKind.OTHER
                    },
                )
            },
            startMs = event.startMs,
            endMs = event.endMs,
            broadcasterNames = listOfNotNull(event.broadcaster),
        )
        val result = FixtureMatcher.match(
            event = matchEvent,
            channels = candidates,
            programmes = programmes,
            preferredLanguages = preferredLanguages,
            nowMs = System.currentTimeMillis(),
        )
        val byId = channels.associateBy { it.id.toString() }
        result.matches.mapNotNull { match ->
            val channel = byId[match.channelId] ?: return@mapNotNull null
            MatchedChannel(channel, sources[channel.sourceId], match, languages[match.channelId])
        }
    }
}

private const val CHANNEL_SNAPSHOT_LIMIT = 60_000
private const val SCORE_SCALE = 10_000
private val DIGITS = Regex("""\d+""")
