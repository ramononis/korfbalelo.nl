package nl.korfbalelo.elo.application

import nl.korfbalelo.elo.ApplicationNew
import nl.korfbalelo.elo.Match
import nl.korfbalelo.elo.RankingEvent
import nl.korfbalelo.elo.RankingNew
import nl.korfbalelo.elo.Team
import nl.korfbalelo.elo.domain.RatingRunWindow
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RatingPipelineTest {
    private data class SeedTeamEvent(
        override val date: LocalDate,
        val teamName: String,
        val place: String,
    ) : RankingEvent() {
        override fun run() {
            RankingNew.add(
                Team(teamName, place, 1500.0).also {
                    it.created = date
                    it.lastDate = date
                },
            )
        }
    }

    private data class PlayMatchEvent(
        override val date: LocalDate,
        val home: String,
        val away: String,
        val homeScore: Int,
        val awayScore: Int,
    ) : RankingEvent() {
        override fun run() {
            Match(home, away, homeScore, awayScore, date).run()
        }
    }

    @Test
    fun `core pipeline runs with in-memory events only`() {
        val date = LocalDate.of(2026, 1, 1)
        val events = setOf<RankingEvent>(
            SeedTeamEvent(date, "Team A", "A"),
            SeedTeamEvent(date, "Team B", "B"),
            PlayMatchEvent(date, "Team A", "Team B", 12, 10),
        )

        val previousLog = ApplicationNew.log
        try {
            ApplicationNew.log = false
            val result = RatingPipeline().run(
                events = events,
                window = RatingRunWindow(date, date),
                activeTeams = emptySet(),
                logEnabled = false,
                onDate = {},
            )

            assertTrue(result.preSplitRanking.isEmpty())
            assertEquals(2, RankingNew.ranking.size)
            assertTrue(RankingNew.ranking.containsKey("Team A"))
            assertTrue(RankingNew.ranking.containsKey("Team B"))
            assertTrue(RankingNew.ranking.getValue("Team A").games >= 1)
            assertTrue(RankingNew.ranking.getValue("Team B").games >= 1)
        } finally {
            ApplicationNew.log = previousLog
        }
    }

    @Test
    fun `snapshot callback fires before first event after requested date`() {
        val seedDate = LocalDate.of(2026, 1, 1)
        val snapshotDate = LocalDate.of(2026, 1, 5)
        val matchDate = LocalDate.of(2026, 1, 10)
        val events = setOf<RankingEvent>(
            SeedTeamEvent(seedDate, "Team A", "A"),
            SeedTeamEvent(seedDate, "Team B", "B"),
            PlayMatchEvent(matchDate, "Team A", "Team B", 12, 10),
        )
        val callbackSnapshots = mutableListOf<Pair<LocalDate, Int>>()

        RatingPipeline().run(
            events = events,
            window = RatingRunWindow(snapshotDate, snapshotDate),
            activeTeams = emptySet(),
            logEnabled = true,
            onDate = { date ->
                callbackSnapshots += date to RankingNew.ranking.getValue("Team A").games
            },
        )

        assertEquals(listOf(snapshotDate to 0), callbackSnapshots)
        assertTrue(RankingNew.ranking.getValue("Team A").games >= 1)
    }
}
