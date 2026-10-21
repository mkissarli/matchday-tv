package dev.matchday.tv.data

import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery

/**
 * Routes football/soccer to ESPN's all-soccer feed, the other launch sports to their
 * ESPN league scoreboards, and leaves cycling/Olympic/other sports to TheSportsDB.
 * The UI only sees one normalised schedule provider.
 */
class CompositeScheduleProvider(
    private val football: FixtureProvider,
    private val otherSports: FixtureProvider,
    private val fallbackSports: FixtureProvider,
) : FixtureProvider {
    override val id: String = "composite"
    override val displayName: String = "ESPN + TheSportsDB"
    override val attribution: String = "ESPN · TheSportsDB"

    override suspend fun events(query: ScheduleQuery): List<ScheduleEvent> {
        val wantsFootball = query.sportIds.isEmpty() || "football" in query.sportIds
        val otherQuery = query.copy(sportIds = query.sportIds - "football")
        val wantsOther = query.sportIds.isEmpty() || otherQuery.sportIds.isNotEmpty()

        val footballResult = if (wantsFootball) {
            runCatching { football.events(query.copy(sportIds = setOf("football"))) }
        } else {
            Result.success(emptyList())
        }
        val otherResult = if (wantsOther) {
            runCatching { otherSports.events(otherQuery) }
        } else {
            Result.success(emptyList())
        }

        val covered = setOf("football", "motorsport", "tennis", "fighting", "american-football")
        val remaining = if (query.sportIds.isEmpty()) {
            setOf("cycling", "olympic", "basketball", "cricket", "rugby", "golf", "other")
        } else {
            query.sportIds - covered
        }
        val fallbackResult = if (remaining.isNotEmpty()) {
            runCatching {
                fallbackSports.events(query.copy(sportIds = remaining))
                    .filter { it.sportId in remaining }
            }
        } else {
            Result.success(emptyList())
        }

        val combined = (
            footballResult.getOrDefault(emptyList()) +
                otherResult.getOrDefault(emptyList()) +
                fallbackResult.getOrDefault(emptyList())
            )
            .filter { query.sportIds.isEmpty() || it.sportId in query.sportIds }
            .distinctBy { it.id }
            .sortedBy { it.startMs }

        if (combined.isEmpty()) {
            listOfNotNull(
                footballResult.exceptionOrNull(),
                otherResult.exceptionOrNull(),
                fallbackResult.exceptionOrNull(),
            ).firstOrNull()?.let { throw it }
        }
        return combined
    }
}
