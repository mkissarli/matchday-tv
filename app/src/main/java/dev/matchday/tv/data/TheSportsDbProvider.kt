package dev.matchday.tv.data

import dev.matchday.domain.schedule.FixtureProvider
import dev.matchday.domain.schedule.ScheduleEvent
import dev.matchday.domain.schedule.ScheduleQuery
import dev.matchday.domain.schedule.TheSportsDbParser
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
 * Free, keyless TheSportsDB V1 schedule adapter.
 *
 * The app uses schedule data only: the parser never reads scores, clocks or standings.
 * GitHub-only distribution avoids TheSportsDB's app-store subscription requirement; the
 * schedule source is attributed in the UI. See THIRD_PARTY_NOTICES.md.
 */
class TheSportsDbProvider(
    client: OkHttpClient,
) : FixtureProvider {

    override val id: String = TheSportsDbParser.PROVIDER_ID
    override val displayName: String = "TheSportsDB"
    override val attribution: String = "Schedule data by TheSportsDB"

    private val http = client.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private data class CachedDay(val atMs: Long, val events: List<ScheduleEvent>)

    private val cache = ConcurrentHashMap<String, CachedDay>()
    private val dayFormat = DateTimeFormatter.ISO_LOCAL_DATE

    override suspend fun events(query: ScheduleQuery): List<ScheduleEvent> = withContext(Dispatchers.IO) {
        val days = daysBetween(query.fromMs, query.toMs)
        val all = ArrayList<ScheduleEvent>()
        // The all-sports feed is dominated by NFL/NCAA on the free tier. Ask for soccer
        // explicitly so "Football" means European/English/Latin American football.
        days.forEach { day ->
            all += fetchDay(day)
        }
        // Broader sports are fetched for the nearest day to stay under the free rate limit.
        days.take(1).forEach { day ->
            SECONDARY_SPORTS.forEach { sport ->
                all += fetchDay(day, sport)
            }
        }
        all.asSequence()
            .filter { query.matches(it) }
            .distinctBy { it.id }
            .sortedBy { it.startMs }
            .toList()
    }

    private fun fetchDay(day: LocalDate, sport: String? = null): List<ScheduleEvent> {
        val date = day.format(dayFormat)
        val key = if (sport == null) date else "$date|$sport"
        cache[key]?.let { if (System.currentTimeMillis() - it.atMs < CACHE_TTL_MS) return it.events }
        val sportQuery = sport?.let { "&s=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val url = "https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$date$sportQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Matchday/0.1 (Android TV)")
            .build()
        repeat(2) { attempt ->
            rateLimit()
            http.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    cache[key]?.let { if (System.currentTimeMillis() - it.atMs < CACHE_TTL_MS) return it.events }
                    if (attempt == 0) {
                        Thread.sleep(2_000)
                        return@use
                    }
                    throw IOException("TheSportsDB rate limit reached. Try again in a minute.")
                }
                if (!response.isSuccessful) throw IOException("TheSportsDB HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val events = TheSportsDbParser.parseEvents(body)
                cache[key] = CachedDay(System.currentTimeMillis(), events)
                return events
            }
        }
        return cache[key]?.events.orEmpty()
    }

    private var lastRequestAtMs = 0L

    private fun rateLimit() {
        val wait = 400L - (System.currentTimeMillis() - lastRequestAtMs)
        if (wait > 0) Thread.sleep(wait)
        lastRequestAtMs = System.currentTimeMillis()
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
        const val MAX_DAYS = 4
        val SECONDARY_SPORTS = listOf("Tennis", "Motorsport", "Cycling", "Fighting", "Olympic Sports")
    }
}
