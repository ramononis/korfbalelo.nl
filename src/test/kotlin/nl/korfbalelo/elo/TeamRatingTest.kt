package nl.korfbalelo.elo

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// AI generated: regression coverage for bounded scoring-pace learning.
class TeamRatingTest {
    @Test
    fun `score learning cap bounds extreme mismatch updates without altering glicko update`() {
        val oldModel = RatingModel.config
        val oldLog = ApplicationNew.log
        val oldSeason = ScoreSeasonality.config
        val oldHome = Team.H
        try {
            ApplicationNew.log = false
            ScoreSeasonality.config = ScoreSeasonalityConfig.off
            val date = LocalDate.of(2020, 1, 1)
            val home = Team("A", "A", 2100.0).also { it.averageScore = 15.0 }
            val away = Team("B", "B", 900.0).also { it.averageScore = 15.0 }
            val match = Match("A", "B", 20, 10, date)
            RatingModel.config = RatingModelConfig.standard.copy(scoreLearningWeightCap = Double.POSITIVE_INFINITY)
            Team.reset()
            val distribution = diffDistroBetween(home, away, date)
            val unbounded = home.newRating(away, 20, 10, true, match, distribution)
            RatingModel.config = RatingModelConfig.standard.copy(scoreLearningWeightCap = 4.0)
            Team.reset()
            val bounded = home.newRating(away, 20, 10, true, match, distribution)
            assertEquals(unbounded.rating, bounded.rating)
            assertEquals(unbounded.rd, bounded.rd)
            assertTrue(kotlin.math.abs(bounded.averageScore - 15.0) <
                kotlin.math.abs(unbounded.averageScore - 15.0))
            assertEquals(15.0 + (20.0 - (15.0 + distribution.first / 2.0)) * 4.0 / 100.0,
                bounded.averageScore, 1e-12)
        } finally {
            RatingModel.config = oldModel
            ApplicationNew.log = oldLog
            ScoreSeasonality.config = oldSeason
            Team.H = oldHome
        }
    }
}
