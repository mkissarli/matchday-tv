package dev.matchday.domain.playback

import dev.matchday.domain.catalog.QualityTier

/**
 * One playable stream the supervisor may choose. A logical channel can expose several candidates:
 * quality tiers for the same feed and sibling channels carrying the same fixture in other
 * languages.
 */
data class PlaybackCandidate(
    val key: String,
    val url: String,
    val channelKey: String,
    val channelName: String,
    val quality: QualityTier = QualityTier.UNKNOWN,
    val languageCode: String? = null,
)

enum class PlaybackFailureKind {
    NETWORK,
    HTTP,
    DECODE,
    STALL,
    UNKNOWN,
}

data class PlaybackSelection(
    val candidate: PlaybackCandidate,
    val reason: String,
)

data class PlaybackSnapshot(
    val current: PlaybackCandidate? = null,
    val failures: Map<String, Int> = emptyMap(),
    val blacklisted: Set<String> = emptySet(),
    val channelFailovers: Int = 0,
    val stable: Boolean = false,
)

/**
 * Pure playback policy: quality fallback plus fixture-level failover.
 *
 * Rules:
 *  - Prefer the viewer's language order (English, French, Arabic, German by default).
 *  - Within a language, prefer the highest quality at or below the configured cap.
 *  - Retry a candidate at most [Config.maxAttemptsPerCandidate] times, then blacklist it for
 *    [Config.blacklistMs]. This is the cooldown that prevents flapping.
 *  - When one channel is exhausted, move to a sibling channel for the same fixture, preferring
 *    English over French over Arabic over German.
 *  - A short stable-playback window clears failure counters, so a recovered stream is not
 *    penalised forever.
 */
class PlaybackSupervisor(
    private val config: Config = Config(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Config(
        val maxAttemptsPerCandidate: Int = 2,
        val blacklistMs: Long = 90_000L,
        val stableAfterMs: Long = 15_000L,
        val retryCooldownMs: Long = 20_000L,
        val maxChannelFailovers: Int = 3,
        val languagePriority: List<String> = listOf("en", "fr", "ar", "de"),
    )

    private var candidates: List<PlaybackCandidate> = emptyList()
    private var current: PlaybackCandidate? = null
    private var currentSinceMs: Long = 0L
    private var channelFailovers: Int = 0
    private val failures = HashMap<String, Int>()
    private val blacklistUntil = HashMap<String, Long>()
    private val retryAfter = HashMap<String, Long>()

    fun start(candidates: List<PlaybackCandidate>, maxTier: QualityTier = QualityTier.UHD): PlaybackSelection? {
        this.candidates = candidates.distinctBy { it.key }
        current = null
        currentSinceMs = 0L
        channelFailovers = 0
        failures.clear()
        blacklistUntil.clear()
        retryAfter.clear()
        return select(reason = "Best available feed", maxTier = maxTier)
    }

    fun current(): PlaybackCandidate? = current

    /** Retry selection after all candidates have cooled down. */
    fun retry(maxTier: QualityTier = QualityTier.UHD): PlaybackSelection? {
        if (current != null) return PlaybackSelection(current!!, "Still playing")
        return select(reason = "Retry after cooldown", maxTier = maxTier)
    }

    fun snapshot(): PlaybackSnapshot = PlaybackSnapshot(
        current = current,
        failures = failures.toMap(),
        blacklisted = activeBlacklist(clock()),
        channelFailovers = channelFailovers,
        stable = current != null && isStable(clock()),
    )

    /**
     * Called when playback reports an error, a stall watchdog expiry, or a decode failure.
     * Returns the next candidate or null when the supervisor has exhausted its options.
     */
    fun onFailure(
        kind: PlaybackFailureKind = PlaybackFailureKind.UNKNOWN,
        maxTier: QualityTier = QualityTier.UHD,
    ): PlaybackSelection? {
        val failed = current ?: return null
        if (isStable(clock())) {
            failures.clear()
            channelFailovers = 0
        }
        val attempts = (failures[failed.key] ?: 0) + 1
        failures[failed.key] = attempts
        retryAfter[failed.key] = clock() + config.retryCooldownMs
        if (attempts >= config.maxAttemptsPerCandidate) {
            blacklistUntil[failed.key] = clock() + config.blacklistMs
        }
        current = null
        currentSinceMs = 0L

        // A sibling variant of the same logical channel comes before leaving the channel.
        val sameChannel = selectFrom(
            pool = candidates.filter { it.channelKey == failed.channelKey && it.key != failed.key },
            reason = "Fallback ${label(kind)} on the same channel",
            maxTier = maxTier,
            countChannelFailover = false,
        )
        if (sameChannel != null) return sameChannel

        if (channelFailovers >= config.maxChannelFailovers) return null
        return selectFrom(
            pool = candidates.filter { it.channelKey != failed.channelKey },
            reason = "Failover after ${label(kind)}",
            maxTier = maxTier,
            countChannelFailover = true,
        )
    }

    /** Call from the player's periodic health check while playback is progressing. */
    fun onPlaying(nowMs: Long = clock()): PlaybackSnapshot {
        val active = current ?: return snapshot()
        if (nowMs - currentSinceMs >= config.stableAfterMs) {
            failures.clear()
            channelFailovers = 0
            blacklistUntil.entries.removeIf { it.value <= nowMs }
            retryAfter.entries.removeIf { it.value <= nowMs }
        }
        return snapshot()
    }

    fun isStable(nowMs: Long = clock()): Boolean =
        current != null && currentSinceMs > 0L && nowMs - currentSinceMs >= config.stableAfterMs

    private fun select(reason: String, maxTier: QualityTier): PlaybackSelection? =
        selectFrom(
            pool = candidates,
            reason = reason,
            maxTier = maxTier,
            countChannelFailover = false,
        )

    private fun selectFrom(
        pool: List<PlaybackCandidate>,
        reason: String,
        maxTier: QualityTier,
        countChannelFailover: Boolean,
    ): PlaybackSelection? {
        val now = clock()
        val available = pool.filter { candidate ->
            (blacklistUntil[candidate.key] ?: 0L) <= now &&
                (retryAfter[candidate.key] ?: 0L) <= now
        }
        if (available.isEmpty()) return null
        val capped = available.filter { it.quality.rank <= maxTier.rank }
        val ranked = (capped.ifEmpty { available })
            .sortedWith(
                compareBy<PlaybackCandidate> { languageRank(it.languageCode) }
                    .thenByDescending { it.quality.rank }
                    .thenBy { it.channelName.lowercase() }
                    .thenBy { it.key },
            )
        val chosen = ranked.firstOrNull() ?: return null
        if (countChannelFailover && current?.channelKey != chosen.channelKey) {
            // current is null at this point; the caller only asks for channel failover after
            // exhausting the previous channel, so every move here is a channel change.
            channelFailovers += 1
        }
        current = chosen
        currentSinceMs = now
        return PlaybackSelection(chosen, reason)
    }

    private fun languageRank(code: String?): Int {
        if (code == null) return config.languagePriority.size + 1
        val normalized = code.trim().lowercase()
        val exact = config.languagePriority.indexOfFirst { it.equals(normalized, ignoreCase = true) }
        if (exact >= 0) return exact
        val prefix = config.languagePriority.indexOfFirst {
            normalized.startsWith(it) || it.startsWith(normalized)
        }
        return if (prefix >= 0) prefix else config.languagePriority.size
    }

    private fun activeBlacklist(now: Long): Set<String> =
        blacklistUntil.filterValues { it > now }.keys

    private fun label(kind: PlaybackFailureKind): String = when (kind) {
        PlaybackFailureKind.NETWORK -> "network failure"
        PlaybackFailureKind.HTTP -> "HTTP failure"
        PlaybackFailureKind.DECODE -> "decode failure"
        PlaybackFailureKind.STALL -> "stall"
        PlaybackFailureKind.UNKNOWN -> "playback failure"
    }
}
