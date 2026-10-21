package dev.matchday.domain.matching

/** What kind of competitor a [MatchParticipant] describes. */
enum class ParticipantKind {
    TEAM,
    PLAYER,
    DRIVER,
    RIDER,
    ATHLETE,
    OTHER,
}

/** One side of a fixture. Aliases cover provider spellings and common nicknames. */
data class MatchParticipant(
    val name: String,
    val shortName: String? = null,
    val aliases: List<String> = emptyList(),
    val kind: ParticipantKind = ParticipantKind.TEAM,
)

/**
 * A fixture/event that may be carried by one or more channels.
 *
 * [startMs] is the scheduled kick-off/start. [endMs] is optional because providers often
 * publish only a start time. All timestamps are epoch milliseconds.
 */
data class MatchEvent(
    val id: String,
    val title: String,
    val sportId: String? = null,
    val competitionName: String? = null,
    val participants: List<MatchParticipant> = emptyList(),
    val startMs: Long,
    val endMs: Long? = null,
    val broadcasterNames: List<String> = emptyList(),
)

/**
 * A channel that could carry [MatchEvent].
 *
 * [logicalName] is the merged/display name when the catalog has one. [nowTitle] and
 * [nextTitle] are optional EPG one-liners already attached to the channel.
 */
data class ChannelCandidate(
    val id: String,
    val rawName: String,
    val logicalName: String? = null,
    val epgChannelId: String? = null,
    val language: String? = null,
    val qualityRank: Int = 0,
    val sourceId: Long = 0L,
    val nowTitle: String? = null,
    val nextTitle: String? = null,
)

/** One EPG row. [channelId] normally refers to [ChannelCandidate.id]. */
data class ProgrammeCandidate(
    val channelId: String,
    val title: String,
    val startMs: Long,
    val stopMs: Long,
)

/** Why a channel was ranked, and whether it is safe to start playback automatically. */
data class StreamMatch(
    val channelId: String,
    val confidence: Double,
    val reasons: List<String>,
    val autoPlayEligible: Boolean,
)

/**
 * Ranked matches. [best] and [hasAutoPlay] are derived by default so callers cannot
 * accidentally publish an inconsistent result.
 */
data class MatchResult(
    val matches: List<StreamMatch>,
    val best: StreamMatch? = matches.firstOrNull(),
    val hasAutoPlay: Boolean = matches.any { it.autoPlayEligible },
)
