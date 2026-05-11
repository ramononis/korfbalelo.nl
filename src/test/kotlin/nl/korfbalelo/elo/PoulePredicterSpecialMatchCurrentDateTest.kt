package nl.korfbalelo.elo

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoulePredicterSpecialMatchCurrentDateTest {
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
    fun `same-day special matches stay visible in fixtures`() {
        val matchDate = LocalDate.of(2026, 4, 11)
        RankingNew.add(Team("Home", "Test", 1510.0).apply { lastDate = matchDate })
        RankingNew.add(Team("Away", "Test", 1490.0).apply { lastDate = matchDate })

        val predicter = PoulePredicter(
            pouleName = "KL",
            teamsToPenalty = mapOf("Home" to 0, "Away" to 0),
            matchesParam = listOf(
                Match("Home", "Away", 20, 22, matchDate).also {
                    it.special = true
                }
            ),
            date = matchDate,
        )

        val snapshot = predicter.currentStanding()
        val results = snapshot.results
        val fixtures = snapshot.fixtures

        assertEquals(1, results.size)
        assertEquals(1, fixtures.size)
        val fixture = fixtures.single()
        assertTrue(fixture.special)
        assertEquals(matchDate, fixture.date)
    }
}
