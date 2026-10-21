package dev.matchday.domain.matching

import kotlin.math.round

/**
 * Deterministic fixture -> stream matcher.
 *
 * The matcher is deliberately precision-first. A channel with both participants in its name
 * (or an in-window EPG row naming both) is allowed to auto-play; a single participant,
 * competition, or broadcaster alone is only a weak/display signal.
 *
 * Time window: the fixture start +/- 3 hours. An EPG row only confirms the event when it
 * overlaps that window, and auto-play additionally requires [nowMs] to sit inside it.
 */
object FixtureMatcher {

    const val AUTO_PLAY_THRESHOLD = 0.82
    const val DISPLAY_THRESHOLD = 0.45
    const val TIME_WINDOW_MS = 3L * 60L * 60L * 1000L

    private const val SCORE_BOTH_TEAMS = 0.86
    private const val SCORE_ONE_TEAM = 0.50
    private const val SCORE_EVENT_TITLE = 0.68
    private const val SCORE_COMPETITION_ONLY = 0.48
    private const val SCORE_BROADCASTER_ONLY = 0.46
    private const val SCORE_EPG_BOTH_TEAMS = 0.90
    private const val SCORE_EPG_EVENT_TITLE = 0.74
    private const val SCORE_EPG_ONE_TEAM_AND_COMPETITION = 0.64
    private const val EPG_CORROBORATION = 0.04

    fun match(
        event: MatchEvent,
        channels: List<ChannelCandidate>,
        programmes: List<ProgrammeCandidate> = emptyList(),
        preferredLanguages: List<String> = emptyList(),
        nowMs: Long = event.startMs,
    ): MatchResult {
        if (channels.isEmpty()) return MatchResult(emptyList())

        val participants = event.participants.map { participant ->
            MatchText.variantsFor(
                participant.name,
                listOfNotNull(participant.shortName) + participant.aliases,
            )
        }
        val windowStart = event.startMs - TIME_WINDOW_MS
        val windowEnd = event.startMs + TIME_WINDOW_MS
        val inWindow = nowMs in windowStart..windowEnd
        val sortedProgrammes = programmes.sortedWith(
            compareBy<ProgrammeCandidate>({ it.channelId }, { it.startMs }, { it.stopMs }, { it.title }),
        )
        val byId = channels.associateBy { it.id }

        val matches = channels.mapNotNull { channel ->
            evaluate(
                event = event,
                participants = participants,
                channel = channel,
                programmes = sortedProgrammes,
                windowStart = windowStart,
                windowEnd = windowEnd,
                inWindow = inWindow,
            )
        }.sortedWith(comparator(preferredLanguages, byId))

        return MatchResult(matches)
    }

    /** Alias with the domain wording used by callers that only need a ranking. */
    fun rank(
        event: MatchEvent,
        channels: List<ChannelCandidate>,
        programmes: List<ProgrammeCandidate> = emptyList(),
        preferredLanguages: List<String> = emptyList(),
        nowMs: Long = event.startMs,
    ): MatchResult = match(event, channels, programmes, preferredLanguages, nowMs)

    private fun evaluate(
        event: MatchEvent,
        participants: List<List<List<String>>>,
        channel: ChannelCandidate,
        programmes: List<ProgrammeCandidate>,
        windowStart: Long,
        windowEnd: Long,
        inWindow: Boolean,
    ): StreamMatch? {
        val nameText = listOfNotNull(channel.rawName, channel.logicalName).joinToString(" ")
        val nameTokens = MatchText.tokens(nameText)
        val metaText = listOfNotNull(channel.nowTitle, channel.nextTitle).joinToString(" ")
        val metaTokens = MatchText.tokens(metaText)

        val nameEvidence = textEvidence(
            event = event,
            participants = participants,
            tokens = nameTokens,
            label = "channel name",
            rawForMarker = nameText,
            allowEventMarker = true,
        )
        val metaEvidence = textEvidence(
            event = event,
            participants = participants,
            tokens = metaTokens,
            label = "now/next title",
            rawForMarker = metaText,
            allowEventMarker = false,
        )
        val epgEvidence = epgEvidence(
            event = event,
            participants = participants,
            channel = channel,
            programmes = programmes,
            windowStart = windowStart,
            windowEnd = windowEnd,
        )

        val nameScore = maxOf(nameEvidence.score, metaEvidence.score)
        var confidence = maxOf(nameScore, epgEvidence.score)
        if (nameScore > 0.0 && epgEvidence.score > 0.0) {
            confidence += EPG_CORROBORATION
        }
        confidence = round3(confidence.coerceIn(0.0, 1.0))
        if (confidence < DISPLAY_THRESHOLD) return null

        val reasons = LinkedHashSet<String>()
        reasons += nameEvidence.reasons
        reasons += metaEvidence.reasons
        reasons += epgEvidence.reasons
        if (!inWindow) reasons += "outside time window"

        val bothParticipants = nameEvidence.bothParticipants || metaEvidence.bothParticipants
        val strongSignal = bothParticipants || epgEvidence.confirmedBothTeams
        val autoPlay = confidence >= AUTO_PLAY_THRESHOLD &&
            event.participants.isNotEmpty() &&
            inWindow &&
            strongSignal &&
            !epgEvidence.blocksAutoPlay

        return StreamMatch(
            channelId = channel.id,
            confidence = confidence,
            reasons = reasons.toList(),
            autoPlayEligible = autoPlay,
        )
    }

    private data class TextEvidence(
        val score: Double,
        val bothParticipants: Boolean,
        val oneParticipant: Boolean,
        val reasons: List<String>,
    )

    private fun textEvidence(
        event: MatchEvent,
        participants: List<List<List<String>>>,
        tokens: List<String>,
        label: String,
        rawForMarker: String,
        allowEventMarker: Boolean,
    ): TextEvidence {
        if (tokens.isEmpty()) return TextEvidence(0.0, false, false, emptyList())

        val reasons = mutableListOf<String>()
        val participantCount = participants.size
        val matched = participants.count { variants ->
            variants.any { phrase -> MatchText.containsPhrase(tokens, phrase) }
        }

        var score = 0.0
        var both = false
        var one = false
        when {
            participantCount >= 2 && matched == participantCount -> {
                score = SCORE_BOTH_TEAMS
                both = true
                reasons += if (participantCount == 2) {
                    "both teams in $label"
                } else {
                    "all participants in $label"
                }
            }

            matched >= 1 -> {
                score = SCORE_ONE_TEAM
                one = true
                reasons += "one team in $label"
            }

            participantCount == 0 && MatchText.titleMatches(event.title, tokens) -> {
                score = SCORE_EVENT_TITLE
                reasons += "event title match"
            }
        }

        if (MatchText.competitionMatches(event.competitionName, tokens)) {
            score = if (score == 0.0) {
                SCORE_COMPETITION_ONLY
            } else {
                score + if (participantCount == 0) 0.04 else if (both) 0.04 else 0.08
            }
            reasons += "competition match"
        }

        val broadcasterMatched = event.broadcasterNames.any { MatchText.broadcasterMatches(it, tokens) }
        if (broadcasterMatched) {
            score = if (score == 0.0) {
                SCORE_BROADCASTER_ONLY
            } else {
                score + if (participantCount == 0) 0.04 else if (both) 0.04 else 0.06
            }
            reasons += "broadcaster match"
        }

        if (allowEventMarker && score > 0.0 && MatchText.looksLikeEventName(rawForMarker)) {
            score += 0.02
            reasons += "event channel name"
        }

        return TextEvidence(
            score = score.coerceAtMost(1.0),
            bothParticipants = both,
            oneParticipant = one,
            reasons = reasons,
        )
    }

    private data class EpgEvidence(
        val score: Double,
        val confirmedBothTeams: Boolean,
        val blocksAutoPlay: Boolean,
        val reasons: List<String>,
    )

    private fun epgEvidence(
        event: MatchEvent,
        participants: List<List<List<String>>>,
        channel: ChannelCandidate,
        programmes: List<ProgrammeCandidate>,
        windowStart: Long,
        windowEnd: Long,
    ): EpgEvidence {
        var bestScore = 0.0
        var bestConfirmed = false
        var bestReason: String? = null
        var matchedOutsideWindow = false
        var strongMatchOutsideWindow = false
        val participantCount = participants.size

        for (programme in programmes) {
            val channelMatches = programme.channelId == channel.id ||
                (channel.epgChannelId != null && programme.channelId == channel.epgChannelId)
            if (!channelMatches) continue

            val tokens = MatchText.tokens(programme.title)
            if (tokens.isEmpty()) continue

            val matched = participants.count { variants ->
                variants.any { phrase -> MatchText.containsPhrase(tokens, phrase) }
            }
            val overlaps = overlapsWindow(programme, windowStart, windowEnd)

            when {
                participantCount >= 2 && matched == participantCount -> {
                    if (overlaps) {
                        if (SCORE_EPG_BOTH_TEAMS > bestScore) {
                            bestScore = SCORE_EPG_BOTH_TEAMS
                            bestConfirmed = true
                            bestReason = "EPG title matches event"
                        }
                    } else {
                        matchedOutsideWindow = true
                        strongMatchOutsideWindow = true
                    }
                }

                participantCount == 0 && MatchText.titleMatches(event.title, tokens) -> {
                    if (overlaps) {
                        if (SCORE_EPG_EVENT_TITLE > bestScore) {
                            bestScore = SCORE_EPG_EVENT_TITLE
                            bestConfirmed = false
                            bestReason = "EPG title matches event"
                        }
                    } else {
                        matchedOutsideWindow = true
                    }
                }

                matched >= 1 && MatchText.competitionMatches(event.competitionName, tokens) -> {
                    if (overlaps) {
                        if (SCORE_EPG_ONE_TEAM_AND_COMPETITION > bestScore) {
                            bestScore = SCORE_EPG_ONE_TEAM_AND_COMPETITION
                            bestConfirmed = false
                            bestReason = "EPG title matches one team + competition"
                        }
                    } else {
                        matchedOutsideWindow = true
                    }
                }
            }
        }

        val reasons = mutableListOf<String>()
        bestReason?.let(reasons::add)
        if (bestScore == 0.0 && matchedOutsideWindow) {
            reasons += "EPG programme outside time window"
        }
        return EpgEvidence(
            score = bestScore,
            confirmedBothTeams = bestConfirmed,
            blocksAutoPlay = strongMatchOutsideWindow && bestScore == 0.0,
            reasons = reasons,
        )
    }

    private fun overlapsWindow(programme: ProgrammeCandidate, windowStart: Long, windowEnd: Long): Boolean {
        val stop = if (programme.stopMs > programme.startMs) programme.stopMs else programme.startMs
        return stop >= windowStart && programme.startMs <= windowEnd
    }

    private fun comparator(
        preferredLanguages: List<String>,
        byId: Map<String, ChannelCandidate>,
    ): Comparator<StreamMatch> = compareByDescending<StreamMatch> { it.confidence }
        .thenBy { MatchText.languageRank(byId[it.channelId]?.language, preferredLanguages) }
        .thenByDescending { byId[it.channelId]?.qualityRank ?: 0 }
        .thenBy { byId[it.channelId]?.sourceId ?: Long.MAX_VALUE }
        .thenBy { it.channelId }

    private fun round3(value: Double): Double = round(value * 1000.0) / 1000.0
}
