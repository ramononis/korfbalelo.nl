package nl.korfbalelo.elo

import kotlin.test.Test
import kotlin.test.assertTrue

class Glicko2CalculatorTest {
    @Test
    fun `expected win increases rating and reduces rating deviation`() {
        val update = Glicko2Calculator.update(
            player = Glicko2Rating(
                rating = 1500.0,
                ratingDeviation = 200.0,
                volatility = Glicko2Calculator.DEFAULT_VOLATILITY,
            ),
            game = Glicko2Game(
                opponent = Glicko2Rating(
                    rating = 1500.0,
                    ratingDeviation = 200.0,
                    volatility = Glicko2Calculator.DEFAULT_VOLATILITY,
                ),
                score = 1.0,
            ),
        )

        assertTrue(update.rating.rating > 1500.0)
        assertTrue(update.rating.ratingDeviation < 200.0)
        assertTrue(update.estimatedImprovement > 0.0)
    }

    @Test
    fun `upset loss decreases rating`() {
        val update = Glicko2Calculator.update(
            player = Glicko2Rating(
                rating = 1500.0,
                ratingDeviation = 100.0,
                volatility = Glicko2Calculator.DEFAULT_VOLATILITY,
            ),
            game = Glicko2Game(
                opponent = Glicko2Rating(
                    rating = 1200.0,
                    ratingDeviation = 50.0,
                    volatility = Glicko2Calculator.DEFAULT_VOLATILITY,
                ),
                score = 0.0,
            ),
        )

        assertTrue(update.rating.rating < 1500.0)
        assertTrue(update.estimatedImprovement < 0.0)
    }

    @Test
    fun `opponent rating offset affects expected score`() {
        val player = Glicko2Rating(
            rating = 1500.0,
            ratingDeviation = 100.0,
            volatility = Glicko2Calculator.DEFAULT_VOLATILITY,
        )
        val opponent = Glicko2Rating(
            rating = 1500.0,
            ratingDeviation = 100.0,
            volatility = Glicko2Calculator.DEFAULT_VOLATILITY,
        )

        val neutral = Glicko2Calculator.update(player, Glicko2Game(opponent, score = 0.5))
        val offsetOpponentDown = Glicko2Calculator.update(
            player,
            Glicko2Game(opponent, score = 0.5, opponentRatingPenalty = 35.0),
        )

        assertTrue(offsetOpponentDown.expectedScore > neutral.expectedScore)
    }

    @Test
    fun `inactive periods increase rating deviation but respect cap`() {
        val aged = Glicko2Calculator.ageRatingDeviation(
            ratingDeviation = 100.0,
            volatility = Glicko2Calculator.DEFAULT_VOLATILITY,
            periods = 500.0,
        )

        assertTrue(aged > 100.0)
        assertTrue(aged <= Glicko2Calculator.MAX_RATING_DEVIATION)
    }
}
