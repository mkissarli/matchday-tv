package dev.matchday.domain.schedule

import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * Parser for ESPN's core tennis competition list — the individual matches inside an
 * ATP/WTA tournament. The site scoreboard only exposes the tournament; this endpoint is
 * what turns it into actual "Player A vs Player B" schedule entries.
 *
 * Scores and set results are deliberately ignored.
 */
object EspnTennisParser {

    fun parse(
        json: String,
        tournamentId: String,
        tournamentName: String,
        providerLeague: String,
    ): List<ScheduleEvent> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val items = root.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<ScheduleEvent>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val competitionId = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val startMs = runCatching {
                OffsetDateTime.parse(item.optString("date")).toInstant().toEpochMilli()
            }.getOrNull() ?: continue
            val competitors = item.optJSONArray("competitors") ?: continue
            val players = buildList {
                for (c in 0 until competitors.length()) {
                    val competitor = competitors.optJSONObject(c) ?: continue
                    val name = competitor.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val id = competitor.optString("id").takeIf { it.isNotBlank() } ?: name
                    add(
                        Participant(
                            id = id,
                            name = name,
                            shortName = null,
                            kind = ParticipantKind.ATHLETE,
                        ),
                    )
                }
            }
            if (players.size < 2) continue
            val type = item.optJSONObject("type")?.optString("text")
                ?.takeIf { it.isNotBlank() }
            val venue = item.optJSONObject("venue")?.optJSONObject("address")
                ?.optString("summary")?.takeIf { it.isNotBlank() }
            out += ScheduleEvent(
                id = "espn:tennis:$tournamentId:$competitionId",
                providerId = "espn-tennis",
                sportId = "tennis",
                competitionId = tournamentId,
                competitionName = providerLeague,
                title = "${players[0].name} vs ${players[1].name}",
                participants = listOf(players[0], players[1]),
                startMs = startMs,
                endMs = null,
                venue = listOfNotNull(tournamentName, venue).joinToString(" · "),
                broadcaster = type,
            )
        }
        return out
    }
}
