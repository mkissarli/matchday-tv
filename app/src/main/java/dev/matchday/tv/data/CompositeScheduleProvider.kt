package dev.matchday.tv.data

import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery

/**
 * Routes football/soccer to the keyless ESPN feed and everything else to TheSportsDB.
 * The UI only sees one normalised schedule provider.
 */
class CompositeScheduleProvider(
    private val football: FixtureProvider,
    private val otherSports: FixtureProvider,
) : FixtureProvider {
    override val id: String = "composite"
    override val displayName: String = "ESPN + TheSportsDB"
    override val attribution: String = "ESPN · TheSportsDB"

    override suspend fun events(query: ScheduleQuery): List<ScheduleEvent> {
        val wantsFootball = query.sportIds.isEmpty() || "football" in query.sportIds
        val wantsOther = query.sportIds.isEmpty() || query.sportIds.any { it != "football" }
        val footballResult = if (wantsFootball) {
            runCatching { football.events(query.copy(sportIds = setOf("football"))) }
        } else {
            Result.success(emptyList())
        }
        val otherResult = if (wantsOther) {
            runCatching { otherSports.events(query.copy(sportIds = query.sportIds - "football")) }
        } else {
            Result.success(emptyList())
        }
        val combined = (footballResult.getOrDefault(emptyList()) + otherResult.getOrDefault(emptyList()))
            .distinctBy { it.id }
            .sortedBy { it.startMs }
        if (combined.isEmpty()) {
            val footballError = footballResult.exceptionOrNull()
            val otherError = otherResult.exceptionOrNull()
            if (footballError != null && otherError != null) throw footballError
            if (footballError != null) throw footballError
            if (otherError != null) throw otherError
        }
        return combined
    }
}
