package dev.matchday.domain.schedule

/**
 * A schedule source. Implementations own transport and caching; this layer only promises that
 * every returned event is normalised into [ScheduleEvent] and lies inside the requested window.
 */
interface FixtureProvider {
    val id: String
    val displayName: String
    val attribution: String

    suspend fun events(query: ScheduleQuery): List<ScheduleEvent>
}
