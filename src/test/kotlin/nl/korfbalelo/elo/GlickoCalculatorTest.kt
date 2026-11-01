package nl.korfbalelo.elo

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// AI generated: check the rating recurrence independently in rating-point units.
class GlickoCalculatorTest {
    @Test
    fun `fractional match score follows the Glicko rating and RD equations`() {
        val player = GlickoRating(1600.0, 140.0)
        val opponent = GlickoRating(1500.0, 100.0)
        val score = 0.73
        val result = GlickoCalculator.update(player, GlickoGame(opponent, score, 35.0))
        val q = ln(10.0) / 400.0
        val g = 1 / sqrt(1 + 3 * q * q * opponent.ratingDeviation.pow(2) / PI.pow(2))
        val expected = 1 / (1 + 10.0.pow(-g * (player.rating - opponent.rating + 35) / 400))
        val precision = 1 / player.ratingDeviation.pow(2) + q * q * g * g * expected * (1 - expected)
        assertEquals(player.rating + q / precision * g * (score - expected), result.rating.rating, 1e-5)
        assertEquals(sqrt(1 / precision), result.rating.ratingDeviation, 1e-5)
        assertTrue(result.rating.ratingDeviation < player.ratingDeviation)
    }

    @Test
    fun `calendar growth depends on elapsed time rather than match count`() {
        val direct = GlickoCalculator.ageRatingDeviation(100.0, 30.0)
        val staged = GlickoCalculator.ageRatingDeviation(GlickoCalculator.ageRatingDeviation(100.0, 10.0), 20.0)
        assertEquals(sqrt(10000.0 + 30 * 10.423068.pow(2)), direct, 1e-12)
        assertEquals(direct, staged, 1e-12)
        assertEquals(450.0, GlickoCalculator.ageRatingDeviation(100.0, 10000.0))
        assertEquals(100.0, GlickoCalculator.ageRatingDeviation(100.0, -10.0))
    }
}
