package dev.matchday.domain.schedule

import java.util.Locale

/**
 * Approximate editorial priority for leagues and competitions.
 *
 * This is deliberately static: it is a proxy for "how worthwhile is this match to watch",
 * not a live ratings feed. Bigger global competitions come first; unfamiliar domestic
 * competitions fall through to the generic bucket.
 */
object LeaguePopularity {

    private data class Rule(val rank: Int, val needles: List<String>)

    // Lower is more prominent. Keep the order roughly aligned with global viewership.
    private val rules = listOf(
        Rule(10, listOf("english premier league")),
        Rule(11, listOf("uefa champions league")),
        Rule(35, listOf("spanish laliga 2", "la liga 2")),
        Rule(35, listOf("italian serie b")),
        Rule(35, listOf("2. bundesliga")),
        Rule(35, listOf("ligue 2")),
        Rule(12, listOf("spanish laliga", "la liga", "laliga")),
        Rule(13, listOf("italian serie a")),
        Rule(14, listOf("german bundesliga")),
        Rule(15, listOf("french ligue 1")),
        Rule(16, listOf("mls")),
        Rule(17, listOf("mexican liga", "liga mx", "liga bbva")),
        Rule(18, listOf("brazilian serie a", "brasileirão", "brasileirao")),
        Rule(19, listOf("argentine liga", "liga profesional")),
        Rule(20, listOf("uefa europa league", "europa league")),
        Rule(21, listOf("conmebol libertadores", "libertadores")),
        Rule(22, listOf("fa cup")),
        Rule(23, listOf("carabao cup", "league cup")),
        Rule(24, listOf("portuguese primeira liga", "primeira liga")),
        Rule(25, listOf("dutch eredivisie", "eredivisie")),
        Rule(26, listOf("turkish süper lig", "super lig", "süper lig")),
        Rule(27, listOf("saudi pro league", "saudi")),
        Rule(28, listOf("english championship")),
        Rule(29, listOf("conmebol sudamericana")),
        Rule(30, listOf("concacaf champions")),
        Rule(31, listOf("russian premier league")),
        Rule(32, listOf("caf champions league")),
        Rule(33, listOf("afc champions league")),
        Rule(40, listOf("nfl", "national football league")),
        Rule(41, listOf("formula 1", "f1")),
        Rule(42, listOf("ufc", "ultimate fighting")),
        Rule(43, listOf("atp")),
        Rule(44, listOf("wta")),
        Rule(60, listOf("japanese j.league", "j1 league", "j.league")),
        Rule(61, listOf("k league", "korea")),
        Rule(80, listOf("club friendly", "friendly")),
    )

    /** Lower rank = more prominent. Unknown labels sort after known ones. */
    fun rank(label: String?): Int {
        val value = label?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (value.isEmpty()) return 500
        rules.forEach { rule ->
            if (rule.needles.any { needle -> value.contains(needle, ignoreCase = true) }) {
                return rule.rank
            }
        }
        return 200
    }
}
