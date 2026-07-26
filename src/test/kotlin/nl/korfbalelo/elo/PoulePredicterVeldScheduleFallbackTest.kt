package nl.korfbalelo.elo

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

// AI generated: verifies the veld preseason schedule fallback when Sportlink has no fixtures.
class PoulePredicterVeldScheduleFallbackTest {
    @BeforeEach
    fun resetState() {
        Team.reset()
        RankingNew.ranking.clear()
        SeasonPredicter.doOutdoor = false
    }

    @AfterEach
    fun resetMode() {
        SeasonPredicter.doOutdoor = false
    }

    @Test
    fun `four-team veld poule uses a double round robin`() {
        SeasonPredicter.doOutdoor = true

        assertEquals(12, predicter(4).baseMatches.count { it != null })
    }

    @Test
    fun `seven-team veld poule uses a single round robin`() {
        SeasonPredicter.doOutdoor = true

        assertEquals(21, predicter(7).baseMatches.count { it != null })
    }

    @Test
    fun `seven-team zaal poule still uses a double round robin`() {
        assertEquals(42, predicter(7).baseMatches.count { it != null })
    }

    private fun predicter(teamCount: Int): PoulePredicter {
        val teamNames = (1..teamCount).map { "Team $it" }
        teamNames.forEach { teamName ->
            RankingNew.add(
                Team(teamName, "test", 1500.0).apply {
                    lastDate = LocalDate.of(2026, 7, 26)
                }
            )
        }
        return PoulePredicter(
            pouleName = "test",
            teamsToPenalty = teamNames.associateWith { 0 },
            matchesParam = emptyList(),
            date = LocalDate.of(2026, 7, 26),
        )
    }
}
