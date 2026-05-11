package nl.korfbalelo.elo

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class PoulePredicterKnownResultTest {
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
    fun `known results override fixture placeholders in poule snapshots`() {
        val matchDate = LocalDate.of(2026, 3, 28)
        RankingNew.add(Team("Home", "Test", 1500.0).apply { lastDate = matchDate })
        RankingNew.add(Team("Away", "Test", 1500.0).apply { lastDate = matchDate })
        ApplicationNew.matches[Triple(matchDate, "Home", "Away")] = Match("Home", "Away", 25, 21, matchDate)

        val predicter = PoulePredicter(
            pouleName = "KL",
            teamsToPenalty = mapOf("Home" to 0, "Away" to 0),
            matchesParam = listOf(Match("Home", "Away", -1, -1, matchDate)),
            date = matchDate,
        )

        val resolved = predicter.matches.single()
        assertEquals(25, resolved.homeScore)
        assertEquals(21, resolved.awayScore)

        val snapshot = predicter.currentStanding() as Map<*, *>
        assertEquals(1, (snapshot["results"] as List<*>).size)
        assertEquals(0, (snapshot["fixtures"] as List<*>).size)
    }

    @Test
    fun `current standing supports transition predicters without a snapshot date`() {
        val matchDate = LocalDate.of(2026, 3, 28)
        RankingNew.add(Team("Home", "Test", 1500.0).apply { lastDate = matchDate })
        RankingNew.add(Team("Away", "Test", 1500.0).apply { lastDate = matchDate })

        val predicter = PoulePredicter(
            pouleName = "KL",
            teamsToPenalty = mapOf("Home" to 0, "Away" to 0),
            matchesParam = listOf(Match("Home", "Away", 25, 21, matchDate)),
            date = null,
        )

        val snapshot = predicter.currentStanding() as Map<*, *>
        assertEquals(1, (snapshot["results"] as List<*>).size)
        assertEquals(0, (snapshot["fixtures"] as List<*>).size)
    }

    @Test
    fun `current standing uses deterministic fallback for fully tied equal-rating teams`() {
        val matchDate = LocalDate.of(2026, 3, 28)
        RankingNew.add(Team("Home", "Test", 1500.0).apply { lastDate = matchDate })
        RankingNew.add(Team("Away", "Test", 1500.0).apply { lastDate = matchDate })

        val predicter = PoulePredicter(
            pouleName = "KL",
            teamsToPenalty = mapOf("Home" to 0, "Away" to 0),
            matchesParam = listOf(Match("Home", "Away", 10, 10, matchDate)),
            date = matchDate,
        )

        val snapshot = predicter.currentStanding() as Map<*, *>
        val standing = snapshot["standing"] as List<*>
        assertEquals(2, standing.size)
    }
}
