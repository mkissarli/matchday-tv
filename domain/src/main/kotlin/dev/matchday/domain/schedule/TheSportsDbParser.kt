package dev.matchday.domain.schedule

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure parser for TheSportsDB V1 `eventsday.php` payloads.
 *
 * The parser never performs IO and never exposes scores or results. Timestamps without an offset
 * (TheSportsDB's `strTimestamp`) are interpreted as UTC, matching the `Z`-less ISO values the API
 * returns. Events without any usable start time are skipped.
 */
object TheSportsDbParser {
    const val PROVIDER_ID = "thesportsdb"

    /** Parse every event in the payload without window filtering. */
    fun parseEvents(json: String): List<ScheduleEvent> =
        parseEvents(json, ScheduleQuery(Long.MIN_VALUE, Long.MAX_VALUE))

    /** Parse and keep only events inside the query window (and sport filter, when set). */
    fun parseEvents(json: String, query: ScheduleQuery): List<ScheduleEvent> {
        if (json.isBlank()) return emptyList()

        val events = runCatching { JSONObject(json).optJSONArray("events") }
            .getOrElse { return emptyList() }
            ?: return emptyList()

        val parsed = ArrayList<ScheduleEvent>(events.length())
        for (index in 0 until events.length()) {
            val raw = events.optJSONObject(index) ?: continue
            val event = runCatching { parseEvent(raw) }.getOrNull() ?: continue
            if (query.matches(event)) parsed += event
        }
        return parsed
    }

    /** Parse a single event object. Returns null when the event has no usable start time. */
    fun parseEvent(raw: JSONObject): ScheduleEvent? {
        val startMs = parseStartMillis(
            timestamp = raw.optStringOrNull("strTimestamp"),
            date = raw.optStringOrNull("dateEvent"),
            time = raw.optStringOrNull("strTime"),
        ) ?: return null

        val sportId = normalizeSport(raw.optStringOrNull("strSport"))
        val home = raw.optStringOrNull("strHomeTeam")
        val away = raw.optStringOrNull("strAwayTeam")
        val title = raw.optStringOrNull("strEvent")
            ?: listOfNotNull(home, away).takeIf { it.isNotEmpty() }?.joinToString(" vs ")
            ?: "Event"

        val idEvent = raw.optStringOrNull("idEvent")
        val eventId = idEvent ?: "$PROVIDER_ID:${startMs}:${title.lowercase(Locale.ROOT)}"

        return ScheduleEvent(
            id = eventId,
            providerId = PROVIDER_ID,
            sportId = sportId,
            competitionId = raw.optStringOrNull("idLeague"),
            competitionName = raw.optStringOrNull("strLeague"),
            title = title,
            participants = participants(
                eventId = eventId,
                sportId = sportId,
                title = title,
                home = home,
                homeBadge = raw.optStringOrNull("strHomeTeamBadge"),
                away = away,
                awayBadge = raw.optStringOrNull("strAwayTeamBadge"),
            ),
            startMs = startMs,
            endMs = null,
            venue = raw.optStringOrNull("strVenue"),
            broadcaster = raw.optStringOrNull("strTVStation"),
            artwork = Artwork(
                eventLogoUrl = raw.optStringOrNull("strThumb")
                    ?: raw.optStringOrNull("strPoster")
                    ?: raw.optStringOrNull("strSquare"),
                competitionLogoUrl = raw.optStringOrNull("strLeagueBadge"),
            ),
            status = parseStatus(
                status = raw.optStringOrNull("strStatus"),
                postponed = raw.optStringOrNull("strPostponed"),
            ),
        )
    }

    /**
     * Normalise TheSportsDB's free-text sport names to the registry ids used by the app.
     * Anything unknown deliberately falls back to "other" so an unexpected sport never hides an
     * event.
     */
    fun normalizeSport(rawSport: String?): String {
        val sport = rawSport?.trim()?.lowercase(Locale.ROOT) ?: return "other"
        return when (sport) {
            "soccer", "football" -> "football"
            "tennis" -> "tennis"
            "motorsport", "racing", "formula 1", "f1", "motogp", "nascar", "indycar", "rally" -> "motorsport"
            "cycling" -> "cycling"
            "fighting", "boxing", "mma", "ufc", "wrestling" -> "fighting"
            "olympic", "olympics", "olympic sports", "athletics", "aquatics", "swimming",
            "gymnastics", "track and field",
            -> "olympic"
            "basketball" -> "basketball"
            "cricket" -> "cricket"
            "rugby", "rugby union", "rugby league" -> "rugby"
            "american football", "nfl", "college football", "ncaa football", "ncaa" -> "american-football"
            "ice hockey", "hockey", "nhl" -> "ice-hockey"
            "golf" -> "golf"
            else -> "other"
        }
    }

    /** Participants that the schedule card should render for a given sport. */
    fun participantKindFor(sportId: String): ParticipantKind =
        if (sportId == "tennis" || sportId == "fighting") ParticipantKind.ATHLETE
        else ParticipantKind.TEAM

    /**
     * Resolve an event start to epoch milliseconds (UTC). Prefers `strTimestamp`, then combines
     * `dateEvent` with `strTime`, and finally falls back to midnight UTC when only a date exists.
     */
    fun parseStartMillis(timestamp: String?, date: String?, time: String?): Long? {
        parseDateTime(timestamp)?.let { return it }

        val rawDate = date?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (rawDate.length > 10) parseDateTime(rawDate)?.let { return it }

        val localDate = runCatching { LocalDate.parse(rawDate.take(10), DateTimeFormatter.ISO_LOCAL_DATE) }
            .getOrNull() ?: return null
        val localTime = parseTime(time) ?: LocalTime.MIDNIGHT
        return LocalDateTime.of(localDate, localTime)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }

    private fun participants(
        eventId: String,
        sportId: String,
        title: String,
        home: String?,
        homeBadge: String?,
        away: String?,
        awayBadge: String?,
    ): List<Participant> {
        val kind = participantKindFor(sportId)

        if (home != null && away != null) {
            return listOf(
                Participant(
                    id = "$eventId:home",
                    name = home,
                    kind = kind,
                    logoUrl = homeBadge,
                ),
                Participant(
                    id = "$eventId:away",
                    name = away,
                    kind = kind,
                    logoUrl = awayBadge,
                ),
            )
        }

        val single = home ?: away
        if (single != null) {
            return listOf(
                Participant(
                    id = "$eventId:home",
                    name = single,
                    kind = kind,
                    logoUrl = if (home != null) homeBadge else awayBadge,
                ),
            )
        }

        // Racing, cycling and other event-style sports have no home/away sides. Represent the
        // event itself so the UI still has something to render in the participant slot.
        return listOf(
            Participant(
                id = "$eventId:event",
                name = title,
                kind = ParticipantKind.OTHER,
            ),
        )
    }

    private fun parseStatus(status: String?, postponed: String?): ScheduleStatus {
        val postponedValue = postponed?.trim()?.lowercase(Locale.ROOT)
        if (postponedValue == "yes" || postponedValue == "true" || postponedValue == "1" ||
            postponedValue == "postponed"
        ) {
            return ScheduleStatus.POSTPONED
        }

        val value = status?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            value.contains("cancel") || value.contains("abandon") ||
                value == "canc" || value == "abd" || value == "aban" -> ScheduleStatus.CANCELLED
            value.contains("postpon") || value == "pst" || value == "ppd" -> ScheduleStatus.POSTPONED
            else -> ScheduleStatus.SCHEDULED
        }
    }

    private fun parseDateTime(raw: String?): Long? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        runCatching {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }
        runCatching { return Instant.parse(value).toEpochMilli() }

        val withT = value.replace(' ', 'T')
        runCatching {
            return LocalDateTime.parse(withT, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }
        runCatching {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }
        return null
    }

    private fun parseTime(raw: String?): LocalTime? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        runCatching { return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME) }
        val match = ClockPattern.find(value) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        val second = match.groupValues[3].toIntOrNull() ?: 0
        return runCatching { LocalTime.of(hour, minute, second) }.getOrNull()
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key) ?: return null
        if (value == JSONObject.NULL) return null
        return value.toString().trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private val ClockPattern = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?""")
}
