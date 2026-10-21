package dev.matchday.tv.data

import android.util.Log
import dev.matchday.domain.catalog.NameNormalizer
import dev.matchday.domain.schedule.Participant
import dev.matchday.domain.schedule.ScheduleEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One broadcaster entry, e.g. "TNT Sports 1" under Great Britain. */
data class BroadcastChannel(
    val country: String,
    val tier: Int,
    val channelName: String,
)

data class BroadcastListing(
    val title: String,
    val countries: List<String>,
    val channels: List<BroadcastChannel>,
)

/**
 * Keyless per-fixture "where to watch" lookup.
 *
 * LiveSoccerTV publishes, for every football fixture, a server-rendered table
 * of broadcasters per country (GB -> TNT Sports, DE -> DAZN, Algeria -> beIN MENA,
 * USA -> Paramount+ ...). We resolve the fixture page from the site's day
 * schedules, parse that table, and rank the regions UK -> US -> FR -> Arabic
 * -> DE -> rest so the first options are the ones the user is most likely to have.
 */
class BroadcastLookup(client: OkHttpClient) {

    private val http = client.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private data class Page(val atMs: Long, val html: String)
    private val pages = ConcurrentHashMap<String, Page>()

    /** @return per-country broadcasters, or null when the fixture was not found. */
    suspend fun lookup(event: ScheduleEvent): BroadcastListing? = withContext(Dispatchers.IO) {
        try {
            if (event.sportId != "football") {
                Log.i(TAG, "lookup skip: sport=${event.sportId}")
                return@withContext null
            }
            val page = fixturePage(event)
            if (page == null) {
                Log.i(TAG, "lookup no fixture page for: ${event.title}")
                return@withContext null
            }
            val listing = parse(page)
            Log.i(TAG, "lookup '${event.title}' -> ${listing?.channels?.size ?: 0} channels from ${listing?.countries?.size ?: 0} countries")
            listing
        } catch (e: Exception) {
            Log.i(TAG, "lookup error: ${e.message}")
            null
        }
    }

    // ---- fixture resolution -------------------------------------------------

    /**
     * Schedules pages for the event's local date, then its UTC date, then the day
     * after the UTC date (late kick-offs can roll onto the next site day list).
     * Each candidate page is matched by kickoff epoch when available, with team
     * name overlap as fallback.
     */
    private fun fixturePage(event: ScheduleEvent): String? {
        val localDate = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val utcDate = Instant.ofEpochMilli(event.startMs).atZone(ZoneOffset.UTC).toLocalDate()
        val ordered = LinkedHashSet<LocalDate>().apply {
            add(localDate); add(utcDate); add(utcDate.plusDays(1))
        }
        var fallback: String? = null
        for (day in ordered) {
            val html = fetch(schedulePath(day)) ?: continue
            matchInPage(html, event)?.let { page ->
                if (page.exact) return page.path
                if (fallback == null) fallback = page.path
            }
        }
        return fallback
    }

    internal data class FixturePage(val path: String, val exact: Boolean)

    internal fun matchInPage(html: String, event: ScheduleEvent): FixturePage? {
        var fallback: FixturePage? = null
        for (segment in html.split("<tr ")) {
            val link = FIXTURE_LINK.find(segment) ?: continue
            val path = link.groupValues[1].substringBefore('#')
            val title = decode(link.groupValues[2])
            if (!teamsOverlap(title, event.participants)) {
                continue
            }
            val epoch = KICKOFF_EPOCH.find(segment)?.groupValues?.get(1)?.toLongOrNull()
            if (epoch != null && Math.abs(epoch - event.startMs) <= ROW_TIME_WINDOW_MS) {
                Log.i(TAG, "fixture exact: $path (title='$title', drift=${epoch - event.startMs}ms)")
                return FixturePage(path, exact = true)
            }
            if (fallback == null) {
                Log.i(TAG, "fixture fallback: $path (title='$title', epoch=$epoch)")
                fallback = FixturePage(path, exact = false)
            }
        }
        return fallback
    }

    private fun teamsOverlap(title: String, participants: List<Participant>): Boolean {
        if (participants.size < 2) return false
        val pageTokens = tokens(title)
        if (pageTokens.isEmpty()) return false
        return participants.take(2).all { participant ->
            val ours = tokens(participant.name)
            if (ours.isEmpty()) return@all true
            val overlap = ours.count { it in pageTokens }
            overlap >= 1 && overlap >= (ours.size + 2) / 3
        }
    }

    // ---- broadcast table parsing --------------------------------------------

    // internal for tests
    internal fun parse(html: String): BroadcastListing? {
        val title = decode(META_TITLE.find(html)?.groupValues?.get(1).orEmpty())
        val channels = ArrayList<BroadcastChannel>()
        val countries = LinkedHashSet<String>()
        for (row in COUNTRY_ROW.findAll(html)) {
            val country = decode(row.groupValues[1])
            val tier = tierOf(country)
            CHANNEL_NAME.findAll(row.groupValues[2])
                .map { decode(it.groupValues[1]) }
                .filter { it.isNotEmpty() && it != "…" && it != "&hellip;" && !it.endsWith("…") }
                .distinct()
                .forEach { name ->
                    channels += BroadcastChannel(country, tier, name)
                    countries += country
                }
        }
        if (channels.isEmpty()) return null
        return BroadcastListing(
            title = title,
            countries = countries.sortedBy { tierOf(it) },
            channels = channels
                .distinctBy { it.country.lowercase() to it.channelName.lowercase() }
                .sortedWith(compareBy({ it.tier }, { it.country }, { sortOrder(it.channelName) })),
        )
    }

    /** Prefer the primary/secular feed first; odd numbers, CONNECT, XTRA last. */
    private fun sortOrder(name: String): Int = when {
        Regex("""(?i)\b(connect|xtra|extra|premium)\b""").containsMatchIn(name) -> 2
        Regex("""(?i)\b(main|primary)\b""").containsMatchIn(name) -> 0
        Regex("""^(\+\s*)?[1](?!\d)?$""").containsMatchIn(name) -> 0
        Regex("""\b[2-9]\b""").containsMatchIn(name) -> 2
        else -> 1
    }

    // ---- network ------------------------------------------------------------

    private fun fetch(path: String): String? {
        val url = if (path.startsWith("http")) path else SITE + path
        pages[url]?.let { cached ->
            if (System.currentTimeMillis() - cached.atMs < PAGE_TTL_MS) return cached.html
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en")
            .build()
        return try {
            execute(request) ?: retryOnce(request) ?: null
        } catch (e: Exception) {
            Log.i(TAG, "fetch $url failed: ${e.message}")
            null
        }
    }

    /** The site (and TV networks) are flaky: one immediate retry. */
    private fun retryOnce(request: Request): String? = try {
        execute(request)
    } catch (e: Exception) {
        Log.i(TAG, "fetch retry failed: ${e.message}")
        null
    }

    private fun execute(request: Request): String? =
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.i(TAG, "fetch ${request.url} -> HTTP ${response.code}")
                return null
            }
            val html = response.body?.string() ?: return null
            pages[request.url.toString()] = Page(System.currentTimeMillis(), html)
            if (pages.size > 24) trimCache()
            Log.i(TAG, "fetch ${request.url} -> ${html.length} chars")
            html
        }

    private fun trimCache() {
        val cutoff = System.currentTimeMillis() - PAGE_TTL_MS
        pages.values.removeIf { it.atMs < cutoff }
    }

    companion object {
        private const val TAG = "BroadcastLookup"
        private val SITE = "https://www.livesoccertv.com"
        /** `/schedules/2026-10-24/` fixture list for a day. */
        private fun schedulePath(day: LocalDate): String = "/schedules/$day/"
        private const val PAGE_TTL_MS = 30 * 60_000L

        private val STOP_TOKENS = setOf("fc", "cf", "ac", "sc", "as", "vs", "v", "afc", "cfc")
        private const val ROW_TIME_WINDOW_MS = 6 * 60 * 60_000L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        /** `<a href="/match/slug/id#row" title="Home vs Away" ... id="g123">Home vs Away</a>` (fragment kept; stripped by the caller) */
        private val FIXTURE_LINK = Regex("""href="(/match/[^"]+)"[^>]*?title="([^"]+)"[^>]*?id="g\d+"""", RegexOption.DOT_MATCHES_ALL)
        private val KICKOFF_EPOCH = Regex("""dv=['"](\d+)['"]""")
        // internal for tests
        internal val COUNTRY_ROW = Regex(
            """<span class="flag [a-z0-9\-_]+">([^<]+)</span></td><td[^>]*>(.*?)</td></tr>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        private val CHANNEL_NAME = Regex("""<a[^>]*>([^<]+)</a>""")
        private val META_TITLE = Regex("""<meta property="og:title" content="([^"]*)"""")

        private val ARAB_COUNTRIES = setOf(
            "algeria", "bahrain", "egypt", "iraq", "jordan", "kuwait", "lebanon", "libya",
            "mauritania", "morocco", "oman", "palestine", "palestinian territories", "qatar",
            "saudi arabia", "sudan", "syria", "tunisia", "united arab emirates", "yemen",
            "west bank", "west sahara", "djibouti", "somalia", "comoros",
        )

        /** UK -> US -> FR -> Arabic -> DE -> rest. */
        fun tierOf(country: String): Int = when (country.trim().lowercase()) {
            "united kingdom", "great britain", "uk", "england", "scotland", "wales", "ireland" -> 1
            "usa", "united states", "us", "united states of america" -> 2
            "france", "monaco" -> 3
            in ARAB_COUNTRIES -> 4
            "germany", "austria", "switzerland" -> 5
            else -> 9
        }
    }
}

private val STOP_TOKENS = setOf("fc", "cf", "ac", "sc", "as", "vs", "v", "afc", "cfc")

private fun tokens(raw: String): List<String> =
    NameNormalizer.normalize(raw)
        .split(' ')
        .filter { it.length >= 2 && it !in STOP_TOKENS }

private fun decode(html: String): String = html
    .replace("&amp;", "&")
    .replace("&hellip;", "…")
    .replace("&#8230;", "…")
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&apos;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .trim()
