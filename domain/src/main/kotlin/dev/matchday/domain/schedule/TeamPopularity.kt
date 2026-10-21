package dev.matchday.domain.schedule

import java.util.Locale

/**
 * A deliberately small "who is worth watching" boost, used only for headline selection.
 *
 * The list is editorial, not a live audience feed. It lets a big club or star rise above
 * a bigger league's mid-table fixture. It does not reorder the full schedule.
 */
object TeamPopularity {

    private val elite = listOf(
        // Global football clubs
        "real madrid", "barcelona", "manchester united", "manchester city", "liverpool",
        "arsenal", "chelsea", "tottenham", "bayern munich", "paris saint-germain",
        "juventus", "ac milan", "inter milan", "borussia dortmund", "atletico madrid",
        "boca juniors", "river plate", "flamengo", "corinthians", "al hilal", "al nassr",
        "inter miami", "la galaxy",
        // Tennis
        "novak djokovic", "carlos alcaraz", "jannik sinner", "iga swiatek",
        "aryna sabalenka", "coco gauff", "naomi osaka", "jasmine paolini",
        "qinwen zheng", "mirra andreeva", "jessica pegula",
        // Motorsport
        "max verstappen", "lewis hamilton", "charles leclerc", "lando norris",
        // Fighting
        "conor mcgregor", "jon jones", "islam makhachev", "alex pereira",
    )

    private val strong = listOf(
        "napoli", "roma", "lazio", "sevilla", "valencia", "benfica", "porto",
        "ajax", "psv", "feyenoord", "galatasaray", "fenerbahce", "besiktas",
        "celtic", "rangers", "marseille", "lyon", "monaco", "leverkusen",
        "rb leipzig", "newcastle", "aston villa", "west ham", "everton", "leeds",
        "palmeiras", "santos", "sao paulo", "cruz azul", "club america",
        "monterrey", "tigres", "al ittihad", "al ahli",
        "daniil medvedev", "alexander zverev", "andrey rublev", "elena rybakina",
        "ons jabeur", "casper ruud", "stefanos tsitsipas", "taylor fritz",
        "holger rune", "alex de minaur", "ben shelton", "jack draper",
        "lorenzo musetti", "tommy paul", "frances tiafoe", "madison keys",
        "barbora krejcikova", "daria kasatkina",
    )

    /** 0 for an ordinary fixture; higher for a headline-worthy participant. */
    fun boost(participants: List<Participant>): Int {
        if (participants.isEmpty()) return 0
        val names = participants.map { it.name.lowercase(Locale.ROOT) }
        var best = 0
        if (names.any { name -> elite.any { name.contains(it) } }) best = 60
        if (best == 0 && names.any { name -> strong.any { name.contains(it) } }) best = 30
        return best
    }
}
