package dev.matchday.domain.schedule

/** What a participant represents on a schedule card. */
enum class ParticipantKind {
    TEAM,
    ATHLETE,
    DRIVER,
    FIGHTER,
    COUNTRY,
    CONSTRUCTOR,
    OTHER,
}

/**
 * One side of a schedule event. Providers supply whatever they know; fields the provider does
 * not expose stay null rather than being invented.
 */
data class Participant(
    val id: String,
    val name: String,
    val shortName: String? = null,
    val kind: ParticipantKind = ParticipantKind.OTHER,
    val logoUrl: String? = null,
    val portraitUrl: String? = null,
    val countryCode: String? = null,
)

data class Artwork(
    val eventLogoUrl: String? = null,
    val competitionLogoUrl: String? = null,
)

enum class ScheduleStatus {
    SCHEDULED,
    POSTPONED,
    CANCELLED,
}

/**
 * Provider-neutral fixture. The app is schedule-only: there is deliberately no score or result
 * field here, and providers must not surface scores.
 */
data class ScheduleEvent(
    val id: String,
    val providerId: String,
    val sportId: String,
    val competitionId: String?,
    val competitionName: String?,
    val title: String,
    val participants: List<Participant>,
    val startMs: Long,
    val endMs: Long? = null,
    val venue: String? = null,
    val broadcaster: String? = null,
    val artwork: Artwork = Artwork(),
    val status: ScheduleStatus = ScheduleStatus.SCHEDULED,
)

/** Inclusive window in epoch milliseconds. An empty sport set means "every sport". */
data class ScheduleQuery(
    val fromMs: Long,
    val toMs: Long,
    val sportIds: Set<String> = emptySet(),
) {
    fun contains(startMs: Long): Boolean = startMs in fromMs..toMs

    /**
     * True when any part of the event overlaps the window. Multi-day events (tennis
     * tournaments, race weekends) started before the window but are still relevant.
     */
    fun overlaps(startMs: Long, endMs: Long?): Boolean {
        val eventEnd = endMs ?: startMs
        return startMs <= toMs && eventEnd >= fromMs
    }

    fun includesSport(sportId: String): Boolean = sportIds.isEmpty() || sportId in sportIds

    fun matches(event: ScheduleEvent): Boolean =
        overlaps(event.startMs, event.endMs) && includesSport(event.sportId)
}
