package dev.matchday.domain.schedule

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * Parser for ESPN's public `soccer/all/scoreboard` payload.
 *
 * Only schedule fields are read. Score and clock fields are deliberately ignored so the
 * Sports Hub remains schedule-only.
 */
object EspnSoccerParser {

    private const val PROVIDER_ID = "espn-soccer"

    fun parse(json: String): List<ScheduleEvent> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<ScheduleEvent>(array.length())
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let(::parseEvent)?.let(out::add)
        }
        return out
    }

    private fun parseEvent(event: JSONObject): ScheduleEvent? {
        val id = event.optString("id").takeIf { it.isNotBlank() } ?: return null
        val date = event.optString("date").takeIf { it.isNotBlank() } ?: return null
        // ESPN emits ISO-8601 without seconds (for example 2026-10-21T16:45Z).
        val startMs = runCatching { OffsetDateTime.parse(date).toInstant().toEpochMilli() }.getOrNull()
            ?: return null
        val competition = event.optJSONArray("competitions")?.optJSONObject(0) ?: JSONObject()
        val competitors = competition.optJSONArray("competitors") ?: return null
        val home = findCompetitor(competitors, "home") ?: return null
        val away = findCompetitor(competitors, "away") ?: return null
        val homeTeam = home.optJSONObject("team") ?: JSONObject()
        val awayTeam = away.optJSONObject("team") ?: JSONObject()
        val homeName = homeTeam.optString("displayName").takeIf { it.isNotBlank() }
            ?: home.optString("displayName").takeIf { it.isNotBlank() }
            ?: return null
        val awayName = awayTeam.optString("displayName").takeIf { it.isNotBlank() }
            ?: away.optString("displayName").takeIf { it.isNotBlank() }
            ?: return null
        val venue = competition.optJSONObject("venue")?.optString("fullName")?.takeIf { it.isNotBlank() }
        val broadcaster = competition.optJSONArray("broadcasts")?.let(::broadcasts)
        val status = competition.optJSONObject("status")?.optJSONObject("type")
            ?.optString("name").orEmpty()
        val competitionName = competition.optJSONArray("notes")
            ?.optJSONObject(0)?.optString("headline")?.takeIf { it.isNotBlank() }
            ?.takeUnless { headline ->
                listOf("1st leg", "2nd leg", "first leg", "second leg", "group stage", "round of")
                    .any { headline.contains(it, ignoreCase = true) }
            }

        return ScheduleEvent(
            id = "espn:$id",
            providerId = PROVIDER_ID,
            sportId = "football",
            competitionId = null,
            competitionName = competitionName,
            title = "$homeName vs $awayName",
            participants = listOf(
                Participant(
                    id = homeTeam.optString("id", homeName),
                    name = homeName,
                    shortName = homeTeam.optString("abbreviation").takeIf { it.isNotBlank() },
                    kind = ParticipantKind.TEAM,
                    logoUrl = homeTeam.optString("logo").takeIf { it.isNotBlank() },
                ),
                Participant(
                    id = awayTeam.optString("id", awayName),
                    name = awayName,
                    shortName = awayTeam.optString("abbreviation").takeIf { it.isNotBlank() },
                    kind = ParticipantKind.TEAM,
                    logoUrl = awayTeam.optString("logo").takeIf { it.isNotBlank() },
                ),
            ),
            startMs = startMs,
            endMs = null,
            venue = venue,
            broadcaster = broadcaster,
            status = if (status.contains("postpon", ignoreCase = true)) ScheduleStatus.POSTPONED else ScheduleStatus.SCHEDULED,
        )
    }

    private fun findCompetitor(competitors: JSONArray, side: String): JSONObject? {
        for (i in 0 until competitors.length()) {
            val candidate = competitors.optJSONObject(i) ?: continue
            if (candidate.optString("homeAway").equals(side, ignoreCase = true)) return candidate
        }
        return null
    }

    private fun broadcasts(array: JSONArray): String? = buildList {
        for (i in 0 until array.length()) {
            val names = array.optJSONObject(i)?.optJSONArray("names") ?: continue
            for (n in 0 until names.length()) {
                names.optString(n).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.distinct().joinToString(", ").takeIf { it.isNotBlank() }
}
