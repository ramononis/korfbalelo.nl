package nl.korfbalelo.elo

import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class Glicko2Rating(
    val rating: Double,
    val ratingDeviation: Double,
    val volatility: Double,
)

data class Glicko2Game(
    val opponent: Glicko2Rating,
    val score: Double,
    val opponentRatingPenalty: Double = 0.0,
)

data class Glicko2Update(
    val rating: Glicko2Rating,
    val normalizedRatingDelta: Double,
    val expectedScore: Double,
    val ratingPeriodVariance: Double,
    val opponentImpact: Double,
    val estimatedImprovement: Double,
)

// AI generated
object Glicko2Calculator {
    const val RATING_SCALE = 173.7178
    const val MAX_RATING_DEVIATION = 350.0
    const val DEFAULT_VOLATILITY = 0.06
    const val VOLATILITY_CONSTRAINT = 0.6
    const val CONVERGENCE_TOLERANCE = 0.000001

    fun ageRatingDeviation(
        ratingDeviation: Double,
        volatility: Double,
        periods: Double,
        maxRatingDeviation: Double = MAX_RATING_DEVIATION,
    ): Double {
        val phi = toPhi(ratingDeviation)
        return min(maxRatingDeviation / RATING_SCALE, sqrt(phi * phi + periods * volatility * volatility)) *
            RATING_SCALE
    }

    fun update(
        player: Glicko2Rating,
        game: Glicko2Game,
        maxRatingDeviation: Double = MAX_RATING_DEVIATION,
    ): Glicko2Update {
        require(game.score in 0.0..1.0) { "Glicko-2 score must be in [0, 1], was ${game.score}" }

        val mu = (player.rating - 1500.0) / RATING_SCALE
        val phi = player.ratingDeviation / RATING_SCALE
        var opponentMu = (game.opponent.rating - 1500.0) / RATING_SCALE
        val opponentPhi = game.opponent.ratingDeviation / RATING_SCALE
        opponentMu -= game.opponentRatingPenalty / RATING_SCALE

        val opponentImpact = 1.0 / sqrt(1.0 + 3.0 * opponentPhi * opponentPhi / PI / PI)
        val ratingDifference = mu - opponentMu
        val expectedScore = 1.0 / (1.0 + exp(-opponentImpact * ratingDifference))
        val variance = 1 / (opponentImpact * opponentImpact * expectedScore * (1.0 - expectedScore))
        val estimatedImprovement = variance * opponentImpact * (game.score - expectedScore)
        val volatility = updatedVolatility(
            phi = phi,
            volatility = player.volatility,
            variance = variance,
            estimatedImprovement = estimatedImprovement,
        )

        val newPhi = min(toPhi(maxRatingDeviation), 1.0 / sqrt(1.0 / phi / phi + 1.0 / variance))
        val normalizedRatingDelta = newPhi * newPhi * (opponentImpact * (game.score - expectedScore))
        check(normalizedRatingDelta.isFinite()) {
            "Glicko-2 rating delta became non-finite for player=$player, game=$game"
        }

        val newMu = mu + normalizedRatingDelta
        return Glicko2Update(
            rating = Glicko2Rating(
                rating = RATING_SCALE * newMu + 1500.0,
                ratingDeviation = newPhi * RATING_SCALE,
                volatility = volatility,
            ),
            normalizedRatingDelta = normalizedRatingDelta,
            expectedScore = expectedScore,
            ratingPeriodVariance = variance,
            opponentImpact = opponentImpact,
            estimatedImprovement = estimatedImprovement,
        )
    }

    private fun updatedVolatility(
        phi: Double,
        volatility: Double,
        variance: Double,
        estimatedImprovement: Double,
    ): Double {
        val logVolatilitySquared = ln(volatility * volatility)
        val objective = volatilityObjective(
            phi = phi,
            variance = variance,
            estimatedImprovement = estimatedImprovement,
            logVolatilitySquared = logVolatilitySquared,
        )

        var lower = logVolatilitySquared
        var upper = if (estimatedImprovement * estimatedImprovement > phi * phi + variance) {
            ln(estimatedImprovement * estimatedImprovement - phi * phi - variance)
        } else {
            var k = 1
            while (objective(logVolatilitySquared - k * VOLATILITY_CONSTRAINT) < 0.0) {
                k++
            }
            logVolatilitySquared - k * VOLATILITY_CONSTRAINT
        }

        var fLower = objective(lower)
        var fUpper = objective(upper)
        while ((upper - lower).absoluteValue > CONVERGENCE_TOLERANCE) {
            val candidate = lower + (lower - upper) * fLower / (fUpper - fLower)
            val fCandidate = objective(candidate)
            if (fCandidate * fUpper <= 0.0) {
                lower = upper
                fLower = fUpper
            } else {
                fLower /= 2.0
            }
            upper = candidate
            fUpper = fCandidate
        }
        return exp(lower / 2.0)
    }

    private fun volatilityObjective(
        phi: Double,
        variance: Double,
        estimatedImprovement: Double,
        logVolatilitySquared: Double,
    ): (Double) -> Double = { x ->
        val volatilitySquared = exp(x)
        volatilitySquared * (estimatedImprovement * estimatedImprovement - phi * phi - variance - volatilitySquared) /
            (2.0 * (phi * phi + variance + volatilitySquared).pow(2)) -
            (x - logVolatilitySquared) / (VOLATILITY_CONSTRAINT * VOLATILITY_CONSTRAINT)
    }

    private fun toPhi(ratingDeviation: Double): Double =
        ratingDeviation / RATING_SCALE
}
