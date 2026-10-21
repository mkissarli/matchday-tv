package dev.matchday.domain.matching

import dev.matchday.domain.catalog.NameNormalizer
import java.util.Locale

/**
 * Name handling for fixture matching.
 *
 * The catalog normaliser deliberately removes bracketed tags because it merges channel
 * identities. Event names have the opposite problem: "PPV 011 [Arsenal vs Tottenham]" keeps
 * its meaning inside the brackets, so we open brackets before delegating to [NameNormalizer].
 * On top of that we apply football-specific aliases (FC/AFC/CF/SC, Spurs/Tottenham,
 * München/Munich, ARS/TOT) so provider spellings converge on the same token phrase.
 */
internal object MatchText {

    private val BRACKETS = Regex("""[\[\]\(\)\{\}]""")
    private val LEADING_EPG_LABEL = Regex("""(?i)^\s*(?:up\s+next|next|now|live|today)\s*[:\-]\s*""")
    private val VS_SEPARATOR = Regex("""(?i)\b(?:vs\.?|versus|v)\b""")
    private val EVENT_MARKER = Regex("""(?i)\b(?:vs|v|versus|next|now|live|event|ppv|feed)\b""")

    /** Letters that NFD does not decompose into a base letter + combining mark. */
    private val SPECIAL_LATIN = mapOf(
        "ß" to "ss", "ẞ" to "ss",
        "ø" to "o", "Ø" to "o",
        "æ" to "ae", "Æ" to "ae",
        "œ" to "oe", "Œ" to "oe",
        "ł" to "l", "Ł" to "l",
        "đ" to "d", "Đ" to "d",
        "ð" to "d", "Ð" to "d",
        "þ" to "th", "Þ" to "th",
        "ı" to "i", "İ" to "i",
    )

    /** Tokens that describe the club rather than identify it. */
    private val DROP_TOKENS = setOf("fc", "afc", "cf", "sc")

    private val TOKEN_ALIASES = mapOf(
        "munchen" to "munich",
        "spurs" to "tottenham",
        "hotspur" to "",
        "borussia" to "",
        "bvb" to "dortmund",
        "ars" to "arsenal",
        "tot" to "tottenham",
        "man" to "manchester",
        "utd" to "united",
        "juve" to "juventus",
        "barca" to "barcelona",
    )

    /** Phrase aliases are applied to both the fixture side and the channel/EPG side. */
    private val PHRASE_ALIASES = mapOf(
        "paris saint germain" to listOf("psg"),
        "formula 1" to listOf("f1"),
        "formula one" to listOf("f1"),
        "f1" to listOf("formula 1"),
        "inter milan" to listOf("inter"),
        "internazionale" to listOf("inter"),
        "bayern munich" to listOf("bayern"),
    )

    private val TITLE_STOPWORDS = setOf(
        "the", "and", "of", "de", "la", "le", "du", "des", "vs", "v", "versus",
        "live", "next", "now", "match", "game", "event", "today", "at", "in", "on",
    )

    private val GENERIC_COMPETITION_TOKENS = setOf(
        "football", "soccer", "sport", "sports", "league", "live", "tv",
        "channel", "event", "match", "game",
    )

    /** Tokens for a name, including provider/diacritic/abbreviation cleanup. */
    fun tokens(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()

        var value = raw
        for ((letter, replacement) in SPECIAL_LATIN) {
            value = value.replace(letter, replacement)
        }
        value = BRACKETS.replace(value, " ")
        value = LEADING_EPG_LABEL.replace(value, " ")
        value = VS_SEPARATOR.replace(value, " ")

        val normalized = NameNormalizer.normalize(value)
        if (normalized.isBlank()) return emptyList()

        return normalized.split(' ')
            .mapNotNull { token ->
                when (val alias = TOKEN_ALIASES[token]) {
                    null -> token
                    "" -> null
                    else -> alias
                }
            }
            .filter { it.isNotBlank() && it !in DROP_TOKENS }
    }

    fun normalize(raw: String): String = tokens(raw).joinToString(" ")

    /**
     * All token phrases for a participant/competition name and its aliases. A phrase is
     * matched contiguously, so "Sky Sports 1" does not match "Sky Sports 2".
     */
    fun variantsFor(name: String?, aliases: List<String> = emptyList()): List<List<String>> {
        val inputs = buildList {
            if (!name.isNullOrBlank()) add(name)
            aliases.filter { it.isNotBlank() }.forEach(::add)
        }
        return inputs.flatMap { phrase ->
            val base = tokens(phrase)
            if (base.isEmpty()) {
                emptyList()
            } else {
                val extras = PHRASE_ALIASES[base.joinToString(" ")].orEmpty()
                    .map(::tokens)
                    .filter { it.isNotEmpty() }
                listOf(base) + extras
            }
        }.distinct()
    }

    fun containsPhrase(haystack: List<String>, phrase: List<String>): Boolean {
        if (phrase.isEmpty() || haystack.isEmpty() || phrase.size > haystack.size) return false
        val last = haystack.size - phrase.size
        for (start in 0..last) {
            var matches = true
            for (offset in phrase.indices) {
                if (haystack[start + offset] != phrase[offset]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    fun competitionMatches(competitionName: String?, haystack: List<String>): Boolean {
        if (competitionName.isNullOrBlank() || haystack.isEmpty()) return false
        val identity = tokens(competitionName)
        if (identity.isEmpty() || identity.all { it in GENERIC_COMPETITION_TOKENS }) return false
        return variantsFor(competitionName).any { containsPhrase(haystack, it) }
    }

    fun broadcasterMatches(broadcasterName: String?, haystack: List<String>): Boolean {
        if (broadcasterName.isNullOrBlank() || haystack.isEmpty()) return false
        return variantsFor(broadcasterName).any { containsPhrase(haystack, it) }
    }

    /**
     * Event titles are longer than channel names. Exact phrase matching is preferred, with a
     * tolerant token-coverage fallback for "Formula 1 British Grand Prix" vs "Sky Sports F1:
     * British Grand Prix".
     */
    fun titleMatches(title: String, haystack: List<String>): Boolean {
        val titleTokens = tokens(title)
        if (titleTokens.isEmpty() || haystack.isEmpty()) return false
        if (containsPhrase(haystack, titleTokens)) return true

        val meaningful = titleTokens.filter { it.length > 1 && it !in TITLE_STOPWORDS }
        if (meaningful.isEmpty()) return false
        if (meaningful.size == 1) {
            return meaningful.single().length >= 4 && meaningful.single() in haystack
        }
        val matched = meaningful.count { it in haystack }
        return matched >= 2 && matched.toDouble() / meaningful.size >= 0.75
    }

    fun looksLikeEventName(raw: String): Boolean = EVENT_MARKER.containsMatchIn(raw)

    fun languageRank(code: String?, preferredLanguages: List<String>): Int {
        if (preferredLanguages.isEmpty()) return 0
        val preferred = preferredLanguages.map { it.trim().uppercase(Locale.ROOT) }
        val actual = code?.trim()?.uppercase(Locale.ROOT)
        val index = preferred.indexOf(actual)
        return when {
            index >= 0 -> index
            actual.isNullOrBlank() -> preferred.size + 1
            else -> preferred.size
        }
    }
}
