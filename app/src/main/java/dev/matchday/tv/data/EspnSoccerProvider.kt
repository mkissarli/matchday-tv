package dev.matchday.tv.data

import dev.matchday.domain.schedule.EspnSoccerParser
import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Keyless ESPN soccer schedule adapter.
 *
 * ESPN's public `soccer/all` scoreboard carries European, English and Latin American
 * competitions with team crests. Matchday reads schedule fields only; scores and status
 * clocks are deliberately ignored.
 */
class EspnSoccerProvider(
    client: OkHttpClient,
) : FixtureProvider {

    override val id: String = "espn-soccer"
    override val displayName: String = "ESPN"
    override val attribution: String = "Football schedule by ESPN"

    private val http = client.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private data class CachedDay(val atMs: Long, val events: List<ScheduleEvent>)
    private val cache = ConcurrentHashMap<String, CachedDay>()
    private val dayFormat = DateTimeFormatter.BASIC_ISO_DATE

    override suspend fun events(query: ScheduleQuery): List<ScheduleEvent> = withContext(Dispatchers.IO) {
        if (query.sportIds.isNotEmpty() && "football" !in query.sportIds) return@withContext emptyList()
        val days = daysBetween(query.fromMs, query.toMs)
        val all = ArrayList<ScheduleEvent>()
        days.forEach { day -> all += fetchDay(day) }
        all.asSequence()
            .filter { query.matches(it) }
            .distinctBy { it.id }
            .sortedBy { it.startMs }
            .toList()
    }

    private fun fetchDay(day: LocalDate): List<ScheduleEvent> {
        val key = day.format(dayFormat)
        cache[key]?.let { if (System.currentTimeMillis() - it.atMs < CACHE_TTL_MS) return it.events }
        val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?dates=$key"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Matchday/0.1 (Android TV)")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ESPN HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val events = EspnSoccerParser.parse(body)
            cache[key] = CachedDay(System.currentTimeMillis(), events)
            return events
        }
    }

    private fun daysBetween(fromMs: Long, toMs: Long): List<LocalDate> {
        val from = Instant.ofEpochMilli(fromMs).atZone(ZoneOffset.UTC).toLocalDate()
        val to = Instant.ofEpochMilli(toMs).atZone(ZoneOffset.UTC).toLocalDate()
        val days = ArrayList<LocalDate>()
        var day = from
        while (!day.isAfter(to) && days.size < MAX_DAYS) {
            days += day
            day = day.plusDays(1)
        }
        return days
    }

    private companion object {
        const val CACHE_TTL_MS = 15 * 60_000L
        const val MAX_DAYS = 7
    }
}
