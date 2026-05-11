package nl.korfbalelo.elo

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nl.korfbalelo.elo.application.DeclarativeSeasonTransitionSimulator
import nl.korfbalelo.elo.application.SeasonTransitionSimulator
import nl.korfbalelo.mijnkorfbal.Scraper
import nl.korfbalelo.mijnkorfbal.Standing
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.util.SplittableRandom
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutdoorAutumnToSpringTransitionTest {
    // KNKV corrected these voorjaar poule placements after the autumn transition completed.
    private val postTransitionSpringCorrections = setOf(
        "Aurora / DKV (IJ)",
        "Ventura Sport",
    )
    private val simulator: SeasonTransitionSimulator =
        DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/veld2526nj__veld2526vj.json"))

    @BeforeEach
    fun resetState() {
        ApplicationNew.log = false
        ApplicationNew.matches.clear()
        Team.reset()
        RankingNew.ranking.clear()
        RankingNew.aliases.clear()
        RankingNew.graph.clear()
        DiscontinuedTeams.clear()
        originMap = mutableMapOf()
        Scraper.outdoorPoules.clear()
        Scraper.indoorPoules.clear()
        Scraper.specialMatches.clear()
        Scraper.activeTeams.clear()
        PoulePredicter.reset()
    }

    @Test
    fun `veld 2526 najaar results map exactly to current voorjaar tiers`() {
        val autumnPoules = loadAutumnPoules()

        autumnPoules.values
            .flatMap { it.first.keys }
            .distinct()
            .forEach { teamName ->
                RankingNew.add(
                    Team(teamName, "test", 1500.0).apply {
                        lastDate = LocalDate.of(2025, 10, 11)
                    }
                )
            }

        val actualTierByTeam = simulator
            .simulate(autumnPoules, SplittableRandom(0))
            .tierByTeam
            .filterKeys { it !in postTransitionSpringCorrections }
        val expectedTierByTeam = expectedTierByTeam()
            .filterKeys { it !in postTransitionSpringCorrections }

        val missingTeams = expectedTierByTeam.keys - actualTierByTeam.keys
        val unexpectedTeams = actualTierByTeam.keys - expectedTierByTeam.keys
        val mismatches = expectedTierByTeam.keys.intersect(actualTierByTeam.keys)
            .filter { expectedTierByTeam.getValue(it) != actualTierByTeam.getValue(it) }
            .associateWith { actualTierByTeam.getValue(it) to expectedTierByTeam.getValue(it) }

        assertTrue(
            missingTeams.isEmpty() && unexpectedTeams.isEmpty() && mismatches.isEmpty(),
            buildString {
                if (missingTeams.isNotEmpty()) appendLine("Missing teams: ${missingTeams.sorted()}")
                if (unexpectedTeams.isNotEmpty()) appendLine("Unexpected teams: ${unexpectedTeams.sorted()}")
                if (mismatches.isNotEmpty()) {
                    appendLine("Tier mismatches:")
                    mismatches.toSortedMap().forEach { (team, tiers) ->
                        appendLine("$team actual=${tiers.first} expected=${tiers.second}")
                    }
                }
            }
        )
        assertEquals(expectedTierByTeam.size, actualTierByTeam.size)
    }

    private fun expectedTierByTeam(): Map<String, String> {
        val type = object : TypeToken<Map<String, Map<String, List<String>>>>() {}.type
        val data: Map<String, Map<String, List<String>>> =
            Gson().fromJson(File("web/public/veld2526vj.json").reader(), type)

        return data.entries
            .flatMap { (tierName, poules) ->
                poules.values.flatten().map { teamName -> teamName to normalizeTierName(tierName) }
            }
            .toMap()
    }

    private fun loadAutumnPoules(): Map<String, nl.korfbalelo.mijnkorfbal.PouleData> =
        File("web/public/csv/veld2526nj")
            .listFiles { file -> file.extension == "csv" }
            .orEmpty()
            .sortedBy { it.name }
            .associate { file ->
                file.nameWithoutExtension to loadAutumnPoule(file)
            }

    private fun loadAutumnPoule(file: File): nl.korfbalelo.mijnkorfbal.PouleData {
        val latestLine = file.readLines().last { it.isNotBlank() }
        val json = latestLine.split('\t', limit = 3)[1]
        val snapshot = Gson().fromJson(json, ExportedPouleSnapshot::class.java)
        val penalties = snapshot.standing.associate { it.team.name to it.stats.penalties.points }
        val matches = (snapshot.results + snapshot.fixtures).map(ExportedMatch::toMatch)
        return penalties to matches
    }

    private fun normalizeTierName(tierName: String): String = when (tierName) {
        "Ereklasse" -> "ek"
        "Ereklasse D" -> "ekd"
        "Hoofdklasse" -> "hk"
        "Overgangsklasse" -> "ok"
        "1e Klasse" -> "1k"
        "2e Klasse" -> "2k"
        "3e Klasse" -> "3k"
        "4e Klasse" -> "4k"
        else -> error("Unexpected tier $tierName")
    }

    private data class ExportedPouleSnapshot(
        val results: List<ExportedMatch>,
        val fixtures: List<ExportedMatch>,
        val standing: List<Standing>,
    )

    private data class ExportedMatch(
        val home: String,
        val away: String,
        val homeScore: Int,
        val awayScore: Int,
        val date: String,
        val special: Boolean = false,
    ) {
        fun toMatch() = Match(home, away, homeScore, awayScore, LocalDate.parse(date)).also {
            it.special = special
        }
    }

}
