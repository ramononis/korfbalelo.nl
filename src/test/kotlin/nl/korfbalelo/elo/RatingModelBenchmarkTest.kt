package nl.korfbalelo.elo

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// AI generated: regression coverage for benchmark chronology and bounded score learning.
class RatingModelBenchmarkTest {
    private data class Seed(override val date: LocalDate, val name: String) : RankingEvent() {
        override fun run() {
            RankingNew.add(Team(name, name, 1500.0).also { it.created = date; it.lastDate = date })
        }
    }

    @Test
    fun `evaluation start retains training while evaluation end excludes future games`() {
        val start = LocalDate.of(2020, 1, 1)
        val evaluation = start.plusDays(7)
        val events = linkedSetOf<RankingEvent>(
            Seed(start, "A"), Seed(start, "B"),
            Match("A", "B", 20, 10, start),
            Match("A", "B", 12, 11, evaluation),
            Match("A", "B", 0, 30, evaluation.plusDays(7)),
        )
        val previousLog = ApplicationNew.log
        val previousModel = RatingModel.config
        val previousScore = ScoreRatingTweak.config
        val previousSeason = ScoreSeasonality.config
        val previousRange = PredictionBenchmark.range
        val previousIgnore = AccuracyTracker.ignoreMatchesBelow
        try {
            ApplicationNew.log = false
            val config = RatingModelBenchmarkConfig(RatingModelConfig.standard,
                ScoreRatingTweakConfig(), ScoreSeasonalityConfig.off)
            val full = RatingModelBenchmark.runVariant(config, start to evaluation, events)
            val trainedRating = RankingNew.ranking.getValue("A").rating
            val late = RatingModelBenchmark.runVariant(config, evaluation to evaluation, events)
            assertEquals(2, full.prediction.matches)
            assertEquals(1, late.prediction.matches)
            assertEquals(2, RankingNew.ranking.getValue("A").games)
            assertEquals(trainedRating, RankingNew.ranking.getValue("A").rating)
        } finally {
            ApplicationNew.log = previousLog
            RatingModel.config = previousModel
            ScoreRatingTweak.config = previousScore
            ScoreSeasonality.config = previousSeason
            PredictionBenchmark.range = previousRange
            AccuracyTracker.ignoreMatchesBelow = previousIgnore
        }
    }

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
