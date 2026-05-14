package nl.korfbalelo.elo.infrastructure

import nl.korfbalelo.elo.Match
import nl.korfbalelo.elo.RankingEvent
import nl.korfbalelo.elo.Team
import nl.korfbalelo.elo.domain.DiscontinuedTeamRow
import nl.korfbalelo.elo.domain.RatingSnapshotRow
import nl.korfbalelo.elo.graph.DyGraph
import nl.korfbalelo.elo.gson
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FileRatingOutputs(
    private val preSplitRankingFile: File = File("ranking.csv"),
    private val webRankingFile: File = File("web/public/ranking.csv"),
    private val discontinuedTeamsFile: File = File("web/public/discontinued.csv"),
    private val originsModuleFile: File = File("web/src/origins.ts"),
    private val graphFile: File = File("web/public/graph.csv"),
    private val globalStatsTickerFile: File = File("web/public/global-stat-ticker.json"),
    private val aggregateDirectory: File = File("aggr"),
) {
    data class AggregateSummary(
        val maxId: Int,
        val matchesVersion: String,
        val matchCount: Int,
        val earliestMatchDate: LocalDate?,
        val latestMatchDate: LocalDate?,
    )

    private data class TickerPayload(
        val latestMatchDate: LocalDate?,
        val currentSeasonStart: LocalDate,
        val items: List<TickerItem>,
    )

    private data class TickerItem(
        val scope: String,
        val scopeLabel: String,
        val from: LocalDate,
        val to: LocalDate,
        val section: String,
        val sectionLabel: String,
        val text: String,
        val value: Int,
        val match: TickerMatch,
        val link: String,
    )

    private data class TickerMatch(
        val date: LocalDate,
        val home: String,
        val away: String,
        val homeScore: Int,
        val awayScore: Int,
        val focusTeam: String?,
    )

    private data class TickerWindow(
        val scope: String,
        val label: String,
        val from: LocalDate,
        val to: LocalDate,
    )

    fun writePreSplitRanking(rows: List<RatingSnapshotRow>) {
        rows.joinToString("\n") {
            "\"${it.name}\",\"${it.rating.toInt()}\",\"${it.rd}\",\"${it.lastDate}\""
        }.let(preSplitRankingFile::writeText)
    }

    fun writeWebRanking(teams: Collection<Team>) {
        teams.sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.name })
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

    fun writeAggregates(events: Set<RankingEvent>, reference: LocalDate = LocalDate.of(2034, 11, 17)): AggregateSummary {
        aggregateDirectory.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var maxId = 0
        var matchCount = 0
        var earliestMatchDate: LocalDate? = null
        var latestMatchDate: LocalDate? = null
        for ((id, groupedEvents) in events
            .groupBy { (it.date.until(reference, ChronoUnit.YEARS)).toInt() / 10 }
            .toSortedMap()
        ) {
            maxId = max(maxId, id)
            val matches = groupedEvents.filterIsInstance<Match>().sortedBy { it.format() }
            val content = matches.joinToString("\n") { match ->
                with(match) {
                    val actualHomeTeam = actualHome ?: error("missing actual home team for aggregate match ${format()}")
                    val actualAwayTeam = actualAway ?: error("missing actual away team for aggregate match ${format()}")
                    listOf(
                        date,
                        actualHomeTeam.name,
                        actualAwayTeam.name,
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
            }
            File(aggregateDirectory, "$id.csv").writeText(content)
            digest.update(id.toString().toByteArray(UTF_8))
            digest.update(0.toByte())
            digest.update(content.toByteArray(UTF_8))
            digest.update(0.toByte())
            matchCount += matches.size
            for (match in matches) {
                if (earliestMatchDate == null || match.date.isBefore(earliestMatchDate)) {
                    earliestMatchDate = match.date
                }
                if (latestMatchDate == null || match.date.isAfter(latestMatchDate)) {
                    latestMatchDate = match.date
                }
            }
        }
        return AggregateSummary(
            maxId = maxId,
            matchesVersion = HexFormat.of().formatHex(digest.digest()),
            matchCount = matchCount,
            earliestMatchDate = earliestMatchDate,
            latestMatchDate = latestMatchDate,
        )
    }

    // AI-assisted: exports compact global records for the frontend newsticker.
    fun writeGlobalStatsTicker(
        events: Set<RankingEvent>,
        currentSeasonStart: LocalDate = LocalDate.of(2025, 8, 1),
    ) {
        val matches = events
            .filterIsInstance<Match>()
            .filter { it.homeScore >= 0 && it.awayScore >= 0 }
            .sortedBy { it.date }
        val latestMatchDate = matches.maxOfOrNull { it.date }
        if (latestMatchDate == null) {
            globalStatsTickerFile.writeText(gson.toJson(TickerPayload(null, currentSeasonStart, emptyList())))
            return
        }
        val windows = listOf(
            TickerWindow("currentSeason", "Dit seizoen", currentSeasonStart, latestMatchDate),
            TickerWindow("last7Days", "Laatste 7 dagen", latestMatchDate.minusDays(6), latestMatchDate),
        )
        val items = windows.flatMap { window ->
            tickerItemsForWindow(
                window,
                matches.filter { !it.date.isBefore(window.from) && !it.date.isAfter(window.to) },
            )
        }
        globalStatsTickerFile.writeText(gson.toJson(TickerPayload(latestMatchDate, currentSeasonStart, items)))
    }

    private fun tickerItemsForWindow(window: TickerWindow, matches: List<Match>): List<TickerItem> =
        listOfNotNull(
            topTickerItems(
                window,
                matches,
                section = "highest-team-score",
                sectionLabel = "Hoogste teamscore",
                value = { maxOf(it.homeScore, it.awayScore) },
                focusTeam = { if (it.homeScore >= it.awayScore) it.actualHomeName() else it.actualAwayName() },
                text = { match, _ ->
                    match.resultText()
                },
            ),
            topTickerItems(
                window,
                matches.filter { it.homeScore != it.awayScore },
                section = "highest-win-margin",
                sectionLabel = "Hoogste winstmarge",
                value = { abs(it.homeScore - it.awayScore) },
                focusTeam = { if (it.homeScore > it.awayScore) it.actualHomeName() else it.actualAwayName() },
                text = { match, _ ->
                    match.resultText()
                },
            ),
            topTickerItems(
                window,
                matches,
                section = "highest-total-goals",
                sectionLabel = "Hoogste totaalscore",
                value = { it.homeScore + it.awayScore },
                focusTeam = { null },
                text = { match, _ ->
                    match.resultText()
                },
            ),
            topTickerItems(
                window,
                matches.filter { it.homeScore == it.awayScore },
                section = "highest-draws",
                sectionLabel = "Hoogste gelijkspel",
                value = { it.homeScore + it.awayScore },
                focusTeam = { null },
                text = { match, _ ->
                    match.resultText()
                },
            ).takeIf { window.scope != "last7Days" },
            topTickerItems(
                window,
                matches.filter { it.homeScore != it.awayScore },
                section = "highest-losing-score",
                sectionLabel = "Hoogste verliezende score",
                value = { min(it.homeScore, it.awayScore) },
                focusTeam = { if (it.homeScore < it.awayScore) it.actualHomeName() else it.actualAwayName() },
                text = { match, _ ->
                    match.resultText()
                },
            ),
        ).flatten()

    private fun topTickerItems(
        window: TickerWindow,
        matches: List<Match>,
        section: String,
        sectionLabel: String,
        value: (Match) -> Int,
        focusTeam: (Match) -> String?,
        text: (Match, Int) -> String,
        limit: Int = 1,
    ): List<TickerItem> =
        matches
            .sortedWith(
                compareByDescending<Match> { value(it) }
                    .thenByDescending { it.date }
                    .thenBy { it.actualHomeName() }
                    .thenBy { it.actualAwayName() },
            )
            .take(limit)
            .map { match ->
                val itemValue = value(match)
                TickerItem(
                    scope = window.scope,
                    scopeLabel = window.label,
                    from = window.from,
                    to = window.to,
                    section = section,
                    sectionLabel = sectionLabel,
                    text = text(match, itemValue),
                    value = itemValue,
                    match = TickerMatch(
                        date = match.date,
                        home = match.actualHomeName(),
                        away = match.actualAwayName(),
                        homeScore = match.homeScore,
                        awayScore = match.awayScore,
                        focusTeam = focusTeam(match),
                    ),
                    link = "/statistieken/${window.from}/${window.to}#$section",
                )
            }

    private fun Match.actualHomeName(): String = actualHome?.name ?: home

    private fun Match.actualAwayName(): String = actualAway?.name ?: away

    private fun Match.resultText(): String = "${actualHomeName()} - ${actualAwayName()} $homeScore-$awayScore"
}
