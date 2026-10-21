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

class MatchRepository(
    private val settings: SettingsRepository,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
) {

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
