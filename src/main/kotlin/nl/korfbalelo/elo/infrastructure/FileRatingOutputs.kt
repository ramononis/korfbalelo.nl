package nl.korfbalelo.elo.infrastructure

import nl.korfbalelo.elo.Match
import nl.korfbalelo.elo.RankingEvent
import nl.korfbalelo.elo.Team
import nl.korfbalelo.elo.domain.DiscontinuedTeamRow
import nl.korfbalelo.elo.domain.RatingSnapshotRow
import nl.korfbalelo.elo.graph.DyGraph
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

class FileRatingOutputs(
    private val preSplitRankingFile: File = File("ranking.csv"),
    private val webRankingFile: File = File("web/public/ranking.csv"),
    private val discontinuedTeamsFile: File = File("web/public/discontinued.csv"),
    private val originsModuleFile: File = File("web/src/origins.ts"),
    private val graphFile: File = File("web/public/graph.csv"),
    private val aggregateDirectory: File = File("aggr"),
) {
    fun writePreSplitRanking(rows: List<RatingSnapshotRow>) {
        rows.joinToString("\n") {
            "\"${it.name}\",\"${it.rating.toInt()}\",\"${it.rd}\",\"${it.lastDate}\""
        }.let(preSplitRankingFile::writeText)
    }

    fun writeWebRanking(teams: Collection<Team>) {
        teams.sortedByDescending { it.rating }
            .joinToString("\n") {
                "\"${it.name}\",\"${it.rating}\",\"${it.lastDate?.toString().orEmpty()}\",\"${it.firstMatchDate?.toString().orEmpty()}\",\"${it.currentDiff}\",\"${it.place}\",\"${it.rd}\",\"${it.rv}\",\"${it.averageScore}\""
            }
            .let(webRankingFile::writeText)
    }

    fun writeDiscontinuedTeams(rows: Collection<DiscontinuedTeamRow>) {
        val header = "name,place,lastEventDate,lastMatchDate,firstMatchDate,topRating,lastRating,matches,fate,fateType,fateTeams"
        val body = rows
            .sortedWith(
                compareByDescending<DiscontinuedTeamRow> { it.lastEventDate }
                    .thenBy(::sortTargetTeam)
                    .thenBy { it.name }
                    .thenByDescending { it.lastMatchDate ?: LocalDate.MIN }
                    .thenBy { it.fate },
            )
            .joinToString("\n") { row ->
                listOf(
                    csv(row.name),
                    csv(row.place),
                    csv(row.lastEventDate.toString()),
                    csv(row.lastMatchDate?.toString().orEmpty()),
                    csv(row.firstMatchDate?.toString().orEmpty()),
                    row.topRating?.toString().orEmpty(),
                    row.lastRating?.toString().orEmpty(),
                    row.matches.toString(),
                    csv(row.fate),
                    row.fateType,
                    csv(row.fateTeams.joinToString("|")),
                ).joinToString(",")
            }
        discontinuedTeamsFile.writeText(if (body.isBlank()) header else "$header\n$body")
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun sortTargetTeam(row: DiscontinuedTeamRow): String =
        when (row.fateType) {
            "rename", "merge_into" -> row.fateTeams.firstOrNull().orEmpty()
            "fuse_new" -> row.fateTeams.lastOrNull().orEmpty()
            else -> ""
        }

    fun writeOrigins(origins: Map<String, Collection<String>>) {
        origins.entries.joinToString("\n") { (team, sourceTeams) ->
            "$team,${sourceTeams.joinToString(",")}"
        }.let {
            originsModuleFile.writeText("export const origins = `$it`;\n")
        }
    }

    fun writeGraph(graph: DyGraph) {
        graph.writeCsv(graphFile)
    }

    fun writeAggregates(events: Set<RankingEvent>, reference: LocalDate = LocalDate.of(2034, 11, 17)): Int {
        aggregateDirectory.mkdirs()
        var maxId = 0
        for ((id, groupedEvents) in events.groupBy { (it.date.until(reference, ChronoUnit.YEARS)).toInt() / 10 }) {
            maxId = max(maxId, id)
            File(aggregateDirectory, "$id.csv").writeText(
                groupedEvents.filterIsInstance<Match>().sortedBy { it.format() }
                    .joinToString("\n") { match ->
                        with(match) {
                            listOf(
                                date,
                                actualHome!!.name,
                                actualAway!!.name,
                                homeScore,
                                awayScore,
                                homeRating.roundToInt(),
                                awayRating.roundToInt(),
                                String.format("%.3f", pHome),
                                String.format("%.3f", pDraw),
                                String.format("%.3f", pAway),
                                guessHome,
                                guessAway,
                                String.format("%.1f", homeDiff),
                                String.format("%.1f", awayDiff),
                                homeRd,
                                awayRd,
                            ).joinToString(",")
                        }
                    },
            )
        }
        return maxId
    }
}
