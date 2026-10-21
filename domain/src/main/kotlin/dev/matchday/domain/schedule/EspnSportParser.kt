package dev.matchday.domain.schedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * Generic parser for ESPN's keyless sport scoreboards.
 *
 * ESPN uses one event shape with optional competitions:
 *  - soccer / NFL: one competition with home/away competitors,
 *  - UFC: one card event with one competition per fight,
 *  - F1: one race-weekend event with one competition per session (FP1, qualifying, race),
 *  - tennis: one tournament event with no competitions, only a start/end date.
 *
 * The parser turns every one of those into schedule events. It never reads scores or clocks.
 */
object EspnSportParser {

    fun parse(
        json: String,
        sportId: String,
        competitionName: String,
        participantKind: ParticipantKind,
    ): List<ScheduleEvent> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val events = root.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<ScheduleEvent>()
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            out += parseEvent(event, sportId, competitionName, participantKind)
        }
        return out
    }

    private fun parseEvent(
        event: JSONObject,
        sportId: String,
        configuredCompetition: String,
        participantKind: ParticipantKind,
    ): List<ScheduleEvent> {
        val eventId = event.optString("id").takeIf { it.isNotBlank() } ?: return emptyList()
        val eventName = event.optString("name").takeIf { it.isNotBlank() }
            ?: event.optString("shortName").takeIf { it.isNotBlank() }
            ?: return emptyList()
        val eventStart = dateMs(event.optString("date")) ?: return emptyList()
        val eventEnd = dateMs(event.optString("endDate"))
        val eventVenue = venueOf(event.optJSONArray("venues"))
            ?: event.optJSONObject("circuit")?.optString("fullName")?.takeIf { it.isNotBlank() }
        val eventStatus = statusOf(event.optJSONObject("status"))
        val competitions = event.optJSONArray("competitions")

        if (competitions == null || competitions.length() == 0) {
            // Tennis tournaments arrive without competitions.
            return listOf(
                ScheduleEvent(
                    id = "espn:$sportId:$eventId",
                    providerId = "espn-$sportId",
                    sportId = sportId,
                    competitionId = null,
                    competitionName = configuredCompetition,
                    title = eventName,
                    participants = emptyList(),
                    startMs = eventStart,
                    endMs = eventEnd,
                    venue = eventVenue,
                    status = eventStatus,
                ),
            )
        }

        val out = ArrayList<ScheduleEvent>(competitions.length())
        for (index in 0 until competitions.length()) {
            val competition = competitions.optJSONObject(index) ?: continue
            val competitionId = competition.optString("id").takeIf { it.isNotBlank() }
            val start = dateMs(competition.optString("date")) ?: eventStart
            val end = dateMs(competition.optString("endDate"))
                ?: if (competitions.length() == 1) eventEnd else null
            val competitors = competition.optJSONArray("competitors")
            val participants = participantsOf(competitors, participantKind)
            val isFight = sportId == "fighting"
            val type = competition.optJSONObject("type")?.optString("abbreviation")?.takeIf { it.isNotBlank() }
            val title = when {
                participants.size >= 2 && isFight -> {
                    "${participants[0].name} vs ${participants[1].name}"
                }
                participants.size >= 2 -> {
                    val home = participants.firstOrNull { it.id.startsWith("home:") } ?: participants[0]
                    val away = participants.firstOrNull { it.id.startsWith("away:") } ?: participants[1]
                    "${home.name} vs ${away.name}"
                }
                competitions.length() > 1 && type != null -> "$eventName · $type"
                else -> eventName
            }
            val venue = competition.optJSONObject("venue")?.optString("fullName")?.takeIf { it.isNotBlank() }
                ?: eventVenue
            val broadcaster = broadcasterOf(competition)
            val competitionLabel = if (isFight) {
                event.optString("shortName").takeIf { it.isNotBlank() } ?: configuredCompetition
            } else {
                configuredCompetition
            }
            out += ScheduleEvent(
                id = "espn:$sportId:$eventId:${competitionId ?: index}",
                providerId = "espn-$sportId",
                sportId = sportId,
                competitionId = competitionId,
                competitionName = competitionLabel,
                title = title,
                participants = participants,
                startMs = start,
                endMs = end,
                venue = venue,
                broadcaster = broadcaster,
                status = statusOf(competition.optJSONObject("status")).takeIf {
                    it != ScheduleStatus.SCHEDULED
                } ?: eventStatus,
            )
        }
        return out
    }

    private fun participantsOf(competitors: JSONArray?, kind: ParticipantKind): List<Participant> {
        if (competitors == null || competitors.length() < 2) return emptyList()
        val items = buildList {
            for (i in 0 until competitors.length()) {
                val competitor = competitors.optJSONObject(i) ?: continue
                val team = competitor.optJSONObject("team")
                val athlete = competitor.optJSONObject("athlete")
                val name = team?.optString("displayName")?.takeIf { it.isNotBlank() }
                    ?: athlete?.optString("displayName")?.takeIf { it.isNotBlank() }
                    ?: competitor.optString("displayName").takeIf { it.isNotBlank() }
                    ?: continue
                val side = competitor.optString("homeAway").takeIf { it.isNotBlank() }
                val id = team?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: athlete?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: competitor.optString("id").takeIf { it.isNotBlank() }
                    ?: name
                val portrait = athlete?.optJSONObject("headshot")?.optString("href")
                    ?.takeIf { it.isNotBlank() }
                add(
                    Participant(
                        id = if (side == null) id else "$side:$id",
                        name = name,
                        shortName = team?.optString("abbreviation")?.takeIf { it.isNotBlank() }
                            ?: athlete?.optString("shortName")?.takeIf { it.isNotBlank() },
                        kind = kind,
                        logoUrl = team?.optString("logo")?.takeIf { it.isNotBlank() },
                        portraitUrl = portrait,
                    ),
                )
            }
        }
        if (items.size < 2) return emptyList()
        return listOf(items[0], items[1])
    }

    private fun venueOf(venues: JSONArray?): String? {
        if (venues == null) return null
        for (i in 0 until venues.length()) {
            venues.optJSONObject(i)?.optString("fullName")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun broadcasterOf(competition: JSONObject): String? {
        competition.optString("broadcast").takeIf { it.isNotBlank() }?.let { return it }
        val broadcasts = competition.optJSONArray("broadcasts") ?: return null
        val names = buildList {
            for (i in 0 until broadcasts.length()) {
                val list = broadcasts.optJSONObject(i)?.optJSONArray("names") ?: continue
                for (n in 0 until list.length()) {
                    list.optString(n).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.distinct()
        return names.joinToString(", ").takeIf { it.isNotBlank() }
    }

    private fun statusOf(status: JSONObject?): ScheduleStatus {
        val type = status?.optJSONObject("type")?.optString("name").orEmpty()
        return when {
            type.contains("postpon", ignoreCase = true) -> ScheduleStatus.POSTPONED
            type.contains("cancel", ignoreCase = true) -> ScheduleStatus.CANCELLED
            else -> ScheduleStatus.SCHEDULED
        }
    }

    private fun dateMs(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
    }
}
