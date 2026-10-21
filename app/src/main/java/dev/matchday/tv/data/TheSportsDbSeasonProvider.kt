package dev.matchday.tv.data

import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery
import dev.matchday.domain.schedule.TheSportsDbParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * TheSportsDB season calendars for motorsport series that ESPN does not cover.
 * Free-tier, best-effort: Formula 2, Formula 3, IMSA and Supercars.
 */
class TheSportsDbSeasonProvider(
    client: OkHttpClient,
) : FixtureProvider {

    override val id: String = "thesportsdb-seasons"
    override val displayName: String = "TheSportsDB"
    override val attribution: String = "F2/F3/IMSA/Supercars by TheSportsDB"

    private val http = client.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private data class Season(val id: String, val name: String)
    private data class Cached(val atMs: Long, val events: List<ScheduleEvent>)

    private val seasons = listOf(
        Season("4486", "Formula 2"),
        Season("4487", "Formula 3"),
        Season("4488", "IMSA"),
        Season("4489", "Supercars"),
    )
    private val cache = ConcurrentHashMap<String, Cached>()

    override suspend fun events(query: ScheduleQuery): List<ScheduleEvent> = withContext(Dispatchers.IO) {
        val year = LocalDate.now().year
        val results = coroutineScope {
            seasons.map { season ->
                async { runCatching { fetch(season, year) } }
            }.awaitAll()
        }
        val events = results.flatMap { it.getOrDefault(emptyList()) }
        if (events.isEmpty() && results.all { it.isFailure }) {
            throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IOException("TheSportsDB returned no motorsport calendar")
        }
        events.asSequence()
            .filter { query.matches(it) }
            .distinctBy { it.id }
            .sortedBy { it.startMs }
            .toList()
    }

    private fun fetch(season: Season, year: Int): List<ScheduleEvent> {
        val key = "${season.id}:$year"
        cache[key]?.let { if (System.currentTimeMillis() - it.atMs < CACHE_TTL_MS) return it.events }
        val url = "https://www.thesportsdb.com/api/v1/json/123/eventsseason.php?id=${season.id}&s=$year"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Matchday/0.1 (Android TV)")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 429) {
                cache[key]?.let { return it.events }
                throw IOException("TheSportsDB rate limit reached")
            }
            if (!response.isSuccessful) throw IOException("TheSportsDB HTTP ${response.code}")
            val events = TheSportsDbParser.parseEvents(response.body?.string().orEmpty())
            cache[key] = Cached(System.currentTimeMillis(), events)
            return events
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 60 * 60_000L
    }
}
