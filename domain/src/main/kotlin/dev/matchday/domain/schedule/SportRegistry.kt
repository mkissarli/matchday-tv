package dev.matchday.domain.schedule

/** How the UI should lay out participants for a sport. */
enum class ParticipantStyle {
    TEAM_VS_TEAM,
    ATHLETE_VS_ATHLETE,
    EVENT,
    GRID,
    FIGHT_CARD,
    TEAM_EVENT,
}

/**
 * A sport is data, not an enum branch. New sports can be introduced by adding an entry (and,
 * when a provider needs it, its league ids) without touching routing or UI code.
 */
data class SportRegistryEntry(
    val id: String,
    val displayName: String,
    val iconKey: String,
    val order: Int,
    val keywords: List<String>,
    val participantStyle: ParticipantStyle,
    val competitionIds: List<String> = emptyList(),
)

object SportRegistry {
    val football = SportRegistryEntry(
        id = "football",
        displayName = "Football",
        iconKey = "football",
        order = 0,
        keywords = listOf(
            // "Football" is association football: European, English and Latin American.
            // Gridiron codes live under american-football and must never land here.
            "football", "soccer",
            "premier league", "efl", "championship", "fa cup", "carabao cup", "community shield",
            "la liga", "copa del rey", "bundesliga", "dfb pokal", "serie a", "coppa italia",
            "ligue 1", "coupe de france", "eredivisie", "primeira liga", "super lig",
            "saudi pro league", "mls", "liga mx", "brasileirao", "argentina",
            "copa libertadores", "copa sudamericana", "concacaf", "copa america",
            "champions league", "europa league", "conference league", "world cup",
            "euro", "uefa", "fifa",
        ),
        participantStyle = ParticipantStyle.TEAM_VS_TEAM,
        competitionIds = listOf("4328", "4335", "4331", "4332", "4334", "4480", "4346"),
    )

    val tennis = SportRegistryEntry(
        id = "tennis",
        displayName = "Tennis",
        iconKey = "tennis",
        order = 1,
        keywords = listOf(
            "tennis", "atp", "wta", "wimbledon", "roland garros", "us open", "australian open",
        ),
        participantStyle = ParticipantStyle.ATHLETE_VS_ATHLETE,
        competitionIds = listOf("4464", "4517"),
    )

    val motorsport = SportRegistryEntry(
        id = "motorsport",
        displayName = "Motorsport",
        iconKey = "motorsport",
        order = 2,
        keywords = listOf(
            "motorsport", "formula 1", "f1", "motogp", "nascar", "indycar", "rally",
            "grand prix",
        ),
        participantStyle = ParticipantStyle.GRID,
        competitionIds = listOf("4370"),
    )

    val cycling = SportRegistryEntry(
        id = "cycling",
        displayName = "Cycling",
        iconKey = "cycling",
        order = 3,
        keywords = listOf("cycling", "tour de france", "giro", "vuelta", "uci", "worldtour"),
        participantStyle = ParticipantStyle.EVENT,
        competitionIds = listOf("4386"),
    )

    val olympic = SportRegistryEntry(
        id = "olympic",
        displayName = "Olympic sports",
        iconKey = "olympic",
        order = 4,
        keywords = listOf(
            "olympic", "olympics", "aquatics", "athletics", "gymnastics", "swimming",
        ),
        participantStyle = ParticipantStyle.EVENT,
        competitionIds = listOf("4494"),
    )

    val fighting = SportRegistryEntry(
        id = "fighting",
        displayName = "Fighting",
        iconKey = "fighting",
        order = 5,
        keywords = listOf("ufc", "mma", "boxing", "fight", "wwe", "wrestling", "bellator"),
        participantStyle = ParticipantStyle.FIGHT_CARD,
        competitionIds = listOf("4443", "4445"),
    )

    val basketball = SportRegistryEntry(
        id = "basketball",
        displayName = "Basketball",
        iconKey = "basketball",
        order = 6,
        keywords = listOf("basketball", "nba", "euroleague", "wnba"),
        participantStyle = ParticipantStyle.TEAM_VS_TEAM,
        competitionIds = listOf("4387"),
    )

    val cricket = SportRegistryEntry(
        id = "cricket",
        displayName = "Cricket",
        iconKey = "cricket",
        order = 7,
        keywords = listOf("cricket", "ipl", "test match", "t20", "odi"),
        participantStyle = ParticipantStyle.TEAM_VS_TEAM,
        competitionIds = listOf("4468"),
    )

    val rugby = SportRegistryEntry(
        id = "rugby",
        displayName = "Rugby",
        iconKey = "rugby",
        order = 8,
        keywords = listOf("rugby", "six nations", "nrl", "super rugby"),
        participantStyle = ParticipantStyle.TEAM_VS_TEAM,
        competitionIds = listOf("4413"),
    )

    val americanFootball = SportRegistryEntry(
        id = "american-football",
        displayName = "American football",
        iconKey = "american-football",
        order = 9,
        keywords = listOf(
            "nfl", "american football", "college football", "ncaa football",
            "super bowl", "afc", "nfc",
        ),
        participantStyle = ParticipantStyle.TEAM_VS_TEAM,
        competitionIds = listOf("4391"),
    )

    val iceHockey = SportRegistryEntry(
        id = "ice-hockey",
        displayName = "Ice hockey",
        iconKey = "ice-hockey",
        order = 10,
        keywords = listOf("nhl", "ice hockey", "hockey"),
        participantStyle = ParticipantStyle.TEAM_VS_TEAM,
        competitionIds = listOf("4380"),
    )

    val golf = SportRegistryEntry(
        id = "golf",
        displayName = "Golf",
        iconKey = "golf",
        order = 11,
        keywords = listOf("golf", "pga", "lpga", "ryder cup"),
        participantStyle = ParticipantStyle.EVENT,
        competitionIds = listOf("4426"),
    )

    val other = SportRegistryEntry(
        id = "other",
        displayName = "Other",
        iconKey = "other",
        order = 12,
        keywords = emptyList(),
        participantStyle = ParticipantStyle.EVENT,
        competitionIds = emptyList(),
    )

    /** Sports the Sports Hub launches with. */
    val launchSports: List<SportRegistryEntry> = listOf(
        football, tennis, motorsport, cycling, olympic, fighting,
    )

    /** Launch sports first, then the broader set the app already understands. */
    val all: List<SportRegistryEntry> = launchSports + listOf(
        basketball, cricket, rugby, americanFootball, iceHockey, golf, other,
    )

    private val byId: Map<String, SportRegistryEntry> = all.associateBy { it.id }

    fun byIdOrNull(id: String?): SportRegistryEntry? = id?.let(byId::get)

    fun require(id: String): SportRegistryEntry =
        requireNotNull(byId[id]) { "Unknown sport id: $id" }

    fun isLaunchSport(id: String): Boolean = launchSports.any { it.id == id }

    /**
     * Compose this registry with extra (or overriding) entries. Adding a sport is purely a data
     * operation: no enum, route or UI branch has to change.
     */
    fun with(entries: Collection<SportRegistryEntry>): List<SportRegistryEntry> {
        if (entries.isEmpty()) return all
        val incomingById = entries.associateBy { it.id }
        return (all.filterNot { it.id in incomingById } + entries).sortedBy { it.order }
    }
}
