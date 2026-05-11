package nl.korfbalelo.elo

import com.google.gson.Gson
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
import kotlin.test.assertFalse

class OutdoorSpringToAutumnTransitionSimulatorSmokeTest {
    private val simulator: SeasonTransitionSimulator =
        DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/veld2526vj__veld2627nj.json"))

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
    fun `veld declarative simulator assigns every team to a valid najaar tier`() {
        val poules = loadPoules("web/public/csv/veld2526vj")

        poules.values.flatMap { it.first.keys }.distinct().forEach { teamName ->
            RankingNew.add(
                Team(teamName, "test", 1500.0).apply {
                    lastDate = LocalDate.of(2026, 3, 9)
                }
            )
        }

        val result = simulator.simulate(poules, SplittableRandom(0))
        val counts = result.tierByTeam.values.groupingBy { it }.eachCount()
        assertEquals(32, counts["ek"])
        assertEquals(32, counts["hk"])
        assertEquals(32, counts["ok"])
        assertEquals(48, counts["1k"])
        assertEquals(48, counts["2k"])
        assertEquals(48, counts["3k"])
        assertEquals(36, counts["4k"])
        assertFalse("Keizer Karel" in result.tierByTeam)
        assertFalse("SIOS / Leonidas" in result.tierByTeam)
        assertFalse("De Hoeve" in result.tierByTeam)
        assertFalse("Wesstar" in result.tierByTeam)

        val finalists = result.eventsByTeam.filterValues { "final" in it }.keys
        val champions = result.eventsByTeam.filterValues { "champion" in it }.keys
        assertEquals(2, finalists.size)
        assertEquals(1, champions.size)
        assertEquals(true, champions.single() in finalists)
    }

    @Test
    fun `veld vacancy chain can keep the best fourth placed 3k team`() {
        val poules = loadPoules("web/public/csv/veld2526vj", includeFixtures = false)

        poules.values.flatMap { it.first.keys }.distinct().forEach { teamName ->
            RankingNew.add(
                Team(teamName, "test", 1500.0).apply {
                    lastDate = LocalDate.of(2026, 5, 9)
                    rd = 120.0
                    averageScore = 15.0
                }
            )
        }

        val result = simulator.simulate(poules, SplittableRandom(0))
        listOf("Voltreffers (O)", "Koveni", "Ados", "EKC 2000", "Conventus", "EKCA").forEach { teamName ->
            assertEquals("3k", result.tierByTeam[teamName])
        }
        assertEquals("4k", result.tierByTeam["Kesteren"])
    }

    private fun loadPoules(directory: String, includeFixtures: Boolean = true): Map<String, nl.korfbalelo.mijnkorfbal.PouleData> =
        File(directory)
            .listFiles { file -> file.extension == "csv" }
            .orEmpty()
            .sortedBy { it.name }
            .associate { file ->
                file.nameWithoutExtension to loadPoule(file, includeFixtures)
            }

    private fun loadPoule(file: File, includeFixtures: Boolean): nl.korfbalelo.mijnkorfbal.PouleData {
        val latestLine = file.readLines().last { it.isNotBlank() }
        val json = latestLine.split('\t', limit = 3)[1]
        val snapshot = Gson().fromJson(json, ExportedPouleSnapshot::class.java)
        val penalties = snapshot.standing.associate { it.team.name to it.stats.penalties.points }
        val matches = (snapshot.results + if (includeFixtures) snapshot.fixtures else emptyList()).map(ExportedMatch::toMatch)
        return penalties to matches
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
