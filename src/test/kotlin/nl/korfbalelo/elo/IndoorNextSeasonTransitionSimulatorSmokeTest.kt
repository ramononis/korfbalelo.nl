package nl.korfbalelo.elo

import nl.korfbalelo.elo.application.DeclarativeSeasonTransitionSimulator
import nl.korfbalelo.mijnkorfbal.StaticPoules
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.util.SplittableRandom
import kotlin.test.assertEquals

class IndoorNextSeasonTransitionSimulatorSmokeTest {
    private val simulator =
        DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/zaal2627__zaal2728.json"))

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
        PoulePredicter.reset()
    }

    @Test
    fun `zaal 2627 transition keeps all tiers at their target sizes`() {
        val poules = StaticPoules.loadIndoorPoules("zaal2627")

        poules.values.flatMap { it.first.keys }.distinct().forEach { teamName ->
            RankingNew.add(
                Team(teamName, "test", 1500.0).apply {
                    lastDate = LocalDate.of(2026, 3, 1)
                    rd = 120.0
                    averageScore = 15.0
                }
            )
        }

        val result = simulator.simulate(poules, SplittableRandom(0))
        val counts = result.tierByTeam.values.groupingBy { it }.eachCount()
        assertEquals(10, counts["kl"])
        assertEquals(10, counts["kl2"])
        assertEquals(16, counts["hk"])
        assertEquals(32, counts["ok"])
        assertEquals(64, counts["1k"])
        assertEquals(64, counts["2k"])
        assertEquals(80, counts["3k"])
    }
}
