package dev.matchday.domain.catalog

import java.util.Locale

/**
 * Language and country are deliberately separate from genre. The country map follows the
 * conventions IPTV providers actually use ("AR" is Arabic in a channel prefix, not Argentina;
 * "UK" is the United Kingdom, not Ukrainian).
 */
object LanguageCatalog {
    val english = Language("EN", "English")
    val french = Language("FR", "French")
    val arabic = Language("AR", "Arabic")
    val german = Language("DE", "German")
    val spanish = Language("ES", "Spanish")
    val italian = Language("IT", "Italian")
    val portuguese = Language("PT", "Portuguese")
    val dutch = Language("NL", "Dutch")
    val turkish = Language("TR", "Turkish")
    val russian = Language("RU", "Russian")
    val polish = Language("PL", "Polish")
    val greek = Language("EL", "Greek")
    val romanian = Language("RO", "Romanian")
    val swedish = Language("SV", "Swedish")
    val norwegian = Language("NO", "Norwegian")
    val danish = Language("DA", "Danish")
    val finnish = Language("FI", "Finnish")
    val czech = Language("CS", "Czech")
    val hungarian = Language("HU", "Hungarian")
    val hindi = Language("HI", "Hindi")
    val urdu = Language("UR", "Urdu")
    val bengali = Language("BN", "Bengali")
    val chinese = Language("ZH", "Chinese")
    val japanese = Language("JA", "Japanese")
    val korean = Language("KO", "Korean")

    val all: List<Language> = listOf(
        english, french, arabic, german, spanish, italian, portuguese, dutch, turkish, russian,
        polish, greek, romanian, swedish, norwegian, danish, finnish, czech, hungarian, hindi,
        urdu, bengali, chinese, japanese, korean,
    )
    private val byCode = all.associateBy { it.code }

    /** Provider country prefix -> language. Ambiguous codes are resolved the way IPTV uses them. */
    private val countryToLanguage = mapOf(
        "UK" to "EN", "GB" to "EN", "US" to "EN", "USA" to "EN", "CA" to "EN", "AU" to "EN",
        "NZ" to "EN", "IE" to "EN", "IN" to "EN", "ZA" to "EN",
        "FR" to "FR", "BE" to "FR", "LU" to "FR",
        "AR" to "AR", "ARA" to "AR", "SA" to "AR", "AE" to "AR", "QA" to "AR", "EG" to "AR",
        "MA" to "AR", "DZ" to "AR", "TN" to "AR", "IQ" to "AR", "JO" to "AR", "KW" to "AR",
        "DE" to "DE", "AT" to "DE", "CH" to "DE",
        "ES" to "ES", "MX" to "ES", "LAT" to "ES", "CO" to "ES", "CL" to "ES", "PE" to "ES",
        "IT" to "IT", "PT" to "PT", "BR" to "PT", "NL" to "NL", "TR" to "TR", "RU" to "RU",
        "PL" to "PL", "GR" to "EL", "RO" to "RO", "SE" to "SV", "NO" to "NO", "DK" to "DA",
        "FI" to "FI", "CZ" to "CS", "HU" to "HU", "PK" to "UR", "BD" to "BN", "CN" to "ZH",
        "JP" to "JA", "KR" to "KO",
    )

    private val languageWords = mapOf(
        "english" to "EN", "french" to "FR", "arabic" to "AR", "german" to "DE",
        "spanish" to "ES", "italian" to "IT", "portuguese" to "PT", "dutch" to "NL",
        "turkish" to "TR", "russian" to "RU", "polish" to "PL", "greek" to "EL",
        "romanian" to "RO", "swedish" to "SV", "norwegian" to "NO", "danish" to "DA",
        "finnish" to "FI", "czech" to "CS", "hungarian" to "HU", "hindi" to "HI",
        "urdu" to "UR", "bengali" to "BN", "chinese" to "ZH", "japanese" to "JA",
        "korean" to "KO",
    )

    fun byCodeOrNull(code: String?): Language? {
        if (code.isNullOrBlank()) return null
        return byCode[code.trim().uppercase(Locale.ROOT)]
    }

    fun fromCountry(country: String?): Language? =
        country?.let { countryToLanguage[it.trim().uppercase(Locale.ROOT)] }?.let(byCode::get)

    fun detect(rawName: String, explicitLanguage: String?, explicitCountry: String?): Language? {
        byCodeOrNull(explicitLanguage)?.let { return it }
        fromCountry(explicitCountry)?.let { return it }

        val upper = rawName.uppercase(Locale.ROOT)
        val prefix = Regex("""^\s*\|?([A-Z]{2,3})\|?\s*[:|\-]""").find(upper)?.groupValues?.get(1)
        (byCodeOrNull(prefix) ?: fromCountry(prefix))?.let { return it }

        val lower = rawName.lowercase(Locale.ROOT)
        languageWords.entries.firstOrNull { (word, _) -> lower.contains(word) }?.let { return byCode[it.value] }
        return null
    }
}

object GenreCatalog {
    val news = Genre("news", "News")
    val sport = Genre("sport", "Sport")
    val kids = Genre("kids", "Kids")
    val movies = Genre("movies", "Movies")
    val documentary = Genre("documentary", "Documentary")
    val music = Genre("music", "Music")
    val entertainment = Genre("entertainment", "Entertainment")
    val lifestyle = Genre("lifestyle", "Lifestyle")
    val religion = Genre("religion", "Religion")
    val shopping = Genre("shopping", "Shopping")
    val adult = Genre("adult", "Adult")
    val unsorted = Genre("unsorted", "Unsorted")

    val all: List<Genre> = listOf(
        news, sport, kids, movies, documentary, music, entertainment, lifestyle, religion, shopping, adult, unsorted,
    )
    private val byId = all.associateBy { it.id }

    /**
     * Ordered rules. Earlier genres win when a channel name contains several hints.
     * Add a genre by adding its data here; the browse UI reads this list.
     */
    private val rules: List<Pair<Genre, List<String>>> = listOf(
        news to listOf("news", "noticias", "nachrichten", "actualité", "actualite", "info", "cnn", "bbc news", "sky news", "france 24", "al jazeera", "euronews"),
        sport to listOf("sport", "sports", "football", "soccer", "tennis", "motorsport", "formula 1", "f1", "cycling", "ufc", "boxing", "mma", "cricket", "rugby", "golf", "nba", "nfl", "mlb", "nhl", "premier league", "la liga", "bundesliga", "serie a", "ligue 1", "champions league", "eurosport", "dazn"),
        kids to listOf("kids", "cartoon", "cartoons", "children", "junior", "baby", "nick", "cartoon network", "boomerang", "disney junior", "cbebies", "cbeebies"),
        movies to listOf("movie", "movies", "cinema", "film", "films", "premiere", "sky cinema", "canal+ cinéma", "canal+ cinema", "hbo", "starz", "tcm"),
        documentary to listOf("documentary", "documentaries", "discovery", "history", "nat geo", "national geographic", "animal planet", "science", "nature", "investigation"),
        music to listOf("music", "mtv", "vh1", "trace", "clubbing", "hits", "rock", "jazz", "classical"),
        entertainment to listOf("entertainment", "comedy", "reality", "series", "drama", "show", "tv", "e!", "bravo", "tlc"),
        lifestyle to listOf("lifestyle", "food", "travel", "home", "garden", "fashion", "health", "cooking", "diy"),
        religion to listOf("religion", "religious", "islam", "quran", "qur'an", "christian", "catholic", "gospel", "jewish", "hindu"),
        shopping to listOf("shopping", "shop", "teleshopping", "qvc", "hse24"),
        adult to listOf("adult", "xxx", "erotic", "playboy", "hustler", "brazzers"),
    )

    fun byIdOrNull(id: String?): Genre? = id?.let(byId::get)

    fun classify(name: String, groupTitle: String? = null, explicitGenreId: String? = null): Genre {
        byIdOrNull(explicitGenreId)?.let { return it }
        val haystack = listOfNotNull(groupTitle, name).joinToString(" ").lowercase(Locale.ROOT)
        return rules.firstOrNull { (_, words) -> words.any { it in haystack } }?.first ?: unsorted
    }
}

data class SportHint(val id: String, val label: String, val keywords: List<String>)

object SportHintCatalog {
    val all = listOf(
        // Gridiron first: a name like "NFL Football" or "American Football" must not be
        // swallowed by the generic football/soccer hint below.
        SportHint("american-football", "American football", listOf("nfl", "american football", "college football", "ncaa football", "super bowl", "afc", "nfc")),
        SportHint("football", "Football", listOf("football", "soccer", "premier league", "efl", "fa cup", "la liga", "copa del rey", "bundesliga", "serie a", "ligue 1", "eredivisie", "primeira liga", "liga mx", "brasileirao", "copa libertadores", "copa sudamericana", "concacaf", "champions league", "europa league", "world cup", "uefa", "fifa", "mls")),
        SportHint("tennis", "Tennis", listOf("tennis", "atp", "wta", "wimbledon", "roland garros", "us open", "australian open")),
        SportHint("motorsport", "Motorsport", listOf("motorsport", "formula 1", "f1", "motogp", "nascar", "indycar", "rally", "grand prix")),
        SportHint("cycling", "Cycling", listOf("cycling", "tour de france", "giro", "vuelta", "uci", "worldtour")),
        SportHint("olympic", "Olympic sports", listOf("olympic", "olympics", "aquatics", "athletics", "gymnastics", "swimming")),
        SportHint("fighting", "Fighting", listOf("ufc", "mma", "boxing", "fight", "wwe", "wrestling", "bellator")),
        SportHint("cricket", "Cricket", listOf("cricket", "ipl", "test match", "t20", "odi")),
        SportHint("basketball", "Basketball", listOf("basketball", "nba", "euroleague")),
        SportHint("ice-hockey", "Ice hockey", listOf("nhl", "ice hockey", "hockey")),
        SportHint("golf", "Golf", listOf("golf", "pga", "lpga", "ryder cup")),
        SportHint("rugby", "Rugby", listOf("rugby", "six nations", "nrl")),
    )

    fun detect(name: String, groupTitle: String? = null): SportHint? {
        val haystack = listOfNotNull(groupTitle, name).joinToString(" ").lowercase(Locale.ROOT)
        return all.firstOrNull { hint -> hint.keywords.any { it in haystack } }
    }
}
