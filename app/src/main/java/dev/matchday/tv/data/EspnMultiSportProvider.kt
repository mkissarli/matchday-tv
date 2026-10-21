package dev.matchday.tv.data

import dev.matchday.domain.schedule.EspnSportParser
import dev.matchday.domain.schedule.EspnTennisParser
import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ParticipantKind
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Keyless ESPN feeds for the non-football launch sports.
 *
 * One request per league is enough: ESPN's no-date scoreboard returns the current/next
 * event, including an F1 race weekend, the next UFC card, current tennis tournaments and
 * the current NFL week.
 */
class EspnMultiSportProvider(
    client: OkHttpClient,
) : FixtureProvider {

    override val id: String = "espn-multi"
    override val displayName: String = "ESPN"
    override val attribution: String = "Other sports by ESPN"

    private val http = client.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private data class League(
        val path: String,
        val sportId: String,
        val competitionName: String,
        val participantKind: ParticipantKind,
    )

    private data class Cached(val atMs: Long, val events: List<ScheduleEvent>)

    private val leagues = listOf(
        League("racing/f1", "motorsport", "Formula 1", ParticipantKind.DRIVER),
        League("racing/irl", "motorsport", "IndyCar", ParticipantKind.DRIVER),
        League("racing/nascar-premier", "motorsport", "NASCAR Cup Series", ParticipantKind.DRIVER),
        League("racing/nascar-secondary", "motorsport", "NASCAR Xfinity Series", ParticipantKind.DRIVER),
        League("racing/nascar-truck", "motorsport", "NASCAR Truck Series", ParticipantKind.DRIVER),
        League("tennis/atp", "tennis", "ATP", ParticipantKind.ATHLETE),
        League("tennis/wta", "tennis", "WTA", ParticipantKind.ATHLETE),
        League("mma/ufc", "fighting", "UFC", ParticipantKind.FIGHTER),
        League("football/nfl", "american-football", "NFL", ParticipantKind.TEAM),
    )

    private val cache = ConcurrentHashMap<String, Cached>()

    override suspend fun events(query: ScheduleQuery): List<ScheduleEvent> = withContext(Dispatchers.IO) {
        val wanted = leagues.filter { query.sportIds.isEmpty() || it.sportId in query.sportIds }
        if (wanted.isEmpty()) return@withContext emptyList()

        val results = coroutineScope {
            wanted.map { league ->
                async {
                    runCatching { fetch(league) }
                }
            }.awaitAll()
        }
        val events = results.flatMap { it.getOrDefault(emptyList()) }
        if (events.isEmpty() && results.all { it.isFailure }) {
            throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IOException("ESPN returned no schedule")
        }
        events.asSequence()
            .filter { query.matches(it) }
            .distinctBy { it.id }
            .sortedBy { it.startMs }
            .toList()
    }

    private fun fetch(league: League): List<ScheduleEvent> {
        val key = league.path
        cache[key]?.let { if (System.currentTimeMillis() - it.atMs < CACHE_TTL_MS) return it.events }
        val url = "https://site.api.espn.com/apis/site/v2/sports/${league.path}/scoreboard"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Matchday/0.1 (Android TV)")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ESPN HTTP ${response.code} (${league.path})")
            val body = response.body?.string().orEmpty()
            val events = EspnSportParser.parse(
                json = body,
                sportId = league.sportId,
                competitionName = league.competitionName,
                participantKind = league.participantKind,
            )
            val expanded = if (league.path.startsWith("tennis/")) {
                events.flatMap { tournament ->
                    val matches = runCatching { fetchTennisMatches(league, tournament) }
                        .getOrDefault(emptyList())
                    if (matches.isEmpty()) listOf(tournament) else matches
                }
            } else {
                events
            }
            cache[key] = Cached(System.currentTimeMillis(), expanded)
            return expanded
        }
    }

    private fun fetchTennisMatches(league: League, tournament: ScheduleEvent): List<ScheduleEvent> {
        val tournamentId = tournament.id.substringAfterLast(':')
        val cacheKey = "tennis:${league.path}:$tournamentId"
        cache[cacheKey]?.let {
            if (System.currentTimeMillis() - it.atMs < CACHE_TTL_MS) return it.events
        }
        val tour = league.path.substringAfter("tennis/")
        val url = "https://sports.core.api.espn.com/v2/sports/tennis/leagues/" +
            "$tour/events/$tournamentId/competitions?limit=100"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Matchday/0.1 (Android TV)")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ESPN tennis HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val matches = EspnTennisParser.parse(
                json = body,
                tournamentId = tournamentId,
                tournamentName = tournament.title,
                providerLeague = league.competitionName,
            )
            cache[cacheKey] = Cached(System.currentTimeMillis(), matches)
            return matches
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 15 * 60_000L
    }
}
