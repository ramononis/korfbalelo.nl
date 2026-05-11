package nl.korfbalelo.mijnkorfbal

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import nl.korfbalelo.elo.RankingNew
import java.io.File
import java.time.LocalDate

// AI generated
object StaticPoules {
    val defaultSnapshotDate: LocalDate = LocalDate.of(2027, 1, 1)
    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()
    private val type = object : TypeToken<Map<String, Map<String, List<String>>>>() {}.type

    fun hasIndoorPoules(seasonName: String): Boolean =
        fileFor(seasonName).exists()

    fun loadIndoorPoules(seasonName: String): Map<String, PouleData> {
        val tiers = loadTiers(seasonName)

        return tiers.values
            .flatMap { it.entries }
            .associate { (pouleName, teams) ->
                pouleName to (teams.associateWith { 0 } to emptyList())
            }
    }

    fun writeFrontendArtifacts(
        seasonName: String,
        snapshotDate: LocalDate = defaultSnapshotDate,
    ) {
        val ratings = loadRatings()
        val tiers = loadTiers(seasonName)
        File("web/public/$seasonName.json").writeText(prettyGson.toJson(tiers) + "\n")

        val snapshotDirectory = File("web/public/csv/$seasonName").also(File::mkdirs)
        tiers.values
            .flatMap { it.entries }
            .forEach { (pouleName, teams) ->
                writeSnapshotCsv(snapshotDirectory, pouleName, teams, ratings, snapshotDate)
            }
    }

    private fun loadTiers(seasonName: String): Map<String, Map<String, List<String>>> =
        fileFor(seasonName)
            .reader()
            .use { gson.fromJson(it, type) }

    private fun writeSnapshotCsv(
        snapshotDirectory: File,
        pouleName: String,
        teams: List<String>,
        ratings: Map<String, RatingSnapshot>,
        snapshotDate: LocalDate,
    ) {
        val standing = teams.mapIndexed { index, teamName ->
            Standing(
                Team(teamName),
                StandingStats(
                    penalties = StandingPenalties(0),
                    position = index + 1,
                    points = 0,
                    goals = GoalsStats(0, 0),
                    won = 0,
                    draw = 0,
                    lost = 0,
                )
            )
        }
        val pouleData = mapOf(
            "results" to emptyList<Any>(),
            "fixtures" to emptyList<Any>(),
            "standing" to standing,
        )
        val header = listOf("date", "pouleData") + teams.flatMap { listOf(it, it, it, it, it) }
        val row = listOf(snapshotDate.toString(), gson.toJson(pouleData)) + teams.flatMap { teamName ->
            val rating = ratings[teamName] ?: RatingSnapshot(rating = 1500.0, rd = 350.0)
            listOf("0.0", "0.0", "0.0", rating.rating.toString(), rating.rd.toString())
        }
        File(snapshotDirectory, "$pouleName.csv")
            .writeText(header.joinToString("\t") + "\n" + row.joinToString("\t") + "\n")
    }

    private fun loadRatings(): Map<String, RatingSnapshot> =
        RankingNew.ranking
            .takeIf { it.isNotEmpty() }
            ?.mapValues { (_, team) -> RatingSnapshot(team.rating, team.rd) }
            ?: loadRatingsFromPublicCsv()

    private fun loadRatingsFromPublicCsv(): Map<String, RatingSnapshot> =
        File("web/public/ranking.csv")
            .takeIf(File::exists)
            ?.readLines()
            .orEmpty()
            .mapNotNull { line ->
                val fields = parseCsvLine(line)
                if (fields.size < 7) {
                    null
                } else {
                    fields[0] to RatingSnapshot(fields[1].toDouble(), fields[6].toDouble())
                }
            }
            .toMap()

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(field.toString())
                    field.clear()
                }
                else -> field.append(char)
            }
            index++
        }
        result.add(field.toString())
        return result
    }

    private fun fileFor(seasonName: String): File =
        File("static-poules/$seasonName.json")

    private data class RatingSnapshot(
        val rating: Double,
        val rd: Double,
    )
}
