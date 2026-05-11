package nl.korfbalelo.elo

import nl.korfbalelo.elo.domain.DiscontinuedTeamRow
import java.time.LocalDate

// AI generated
object DiscontinuedTeams {
    private val rows = mutableListOf<DiscontinuedTeamRow>()

    fun clear() {
        rows.clear()
    }

    fun all(): List<DiscontinuedTeamRow> = rows.toList()

    fun registerEnd(team: Team, eventDate: LocalDate) {
        add(team, eventDate, "Gestopt", "end", emptyList())
    }

    fun registerRename(team: Team, eventDate: LocalDate, newName: String) {
        add(team, eventDate, "Hernoemd naar $newName", "rename", listOf(newName))
    }

    fun registerMergedInto(team: Team, eventDate: LocalDate, targetName: String) {
        add(team, eventDate, "Opgegaan in $targetName", "merge_into", listOf(targetName))
    }

    fun registerFusedIntoNew(team: Team, eventDate: LocalDate, otherTeams: Collection<String>, newName: String) {
        val others = otherTeams.filterNot { it == team.name }
        val otherText = formatDutchList(others)
        add(team, eventDate, "Gefuseerd met $otherText tot $newName", "fuse_new", others + newName)
    }

    private fun add(team: Team, eventDate: LocalDate, fate: String, fateType: String, fateTeams: List<String>) {
        rows += DiscontinuedTeamRow(
            name = team.name,
            place = team.place,
            lastEventDate = eventDate,
            lastMatchDate = team.lastMatchDate,
            firstMatchDate = team.firstMatchDate,
            topRating = team.topRating.takeUnless(Double::isNaN),
            lastRating = team.rating.takeUnless { it == Team.MAGIC_1500 },
            matches = team.games,
            fate = fate,
            fateType = fateType,
            fateTeams = fateTeams,
        )
    }

    private fun formatDutchList(names: Collection<String>): String {
        val filtered = names.filter(String::isNotBlank).distinct()
        return when (filtered.size) {
            0 -> "onbekend"
            1 -> filtered.first()
            2 -> "${filtered[0]} en ${filtered[1]}"
            else -> "${filtered.dropLast(1).joinToString(", ")} en ${filtered.last()}"
        }
    }
}
