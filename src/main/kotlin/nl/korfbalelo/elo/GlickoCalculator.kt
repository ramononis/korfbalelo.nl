package nl.korfbalelo.elo

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

data class GlickoRating(
    val rating: Double,
    val ratingDeviation: Double,
)

data class GlickoGame(
    val opponent: GlickoRating,
    val score: Double,
    val opponentRatingPenalty: Double = 0.0,
)

data class GlickoUpdate(
    val rating: GlickoRating,
    val normalizedRatingDelta: Double,
    val expectedScore: Double,
    val ratingPeriodVariance: Double,
    val opponentImpact: Double,
    val estimatedImprovement: Double,
)

// AI generated
object GlickoCalculator {
    const val RATING_SCALE = 173.7178
    const val MAX_RATING_DEVIATION = 450.0
    // AI generated: rating points per square root of elapsed days.
    const val DEFAULT_RD_GROWTH = 10.423068

    // AI generated: calendar time adds variance at the same rate for every team.
    fun ageRatingDeviation(
        ratingDeviation: Double,
        elapsedDays: Double,
        growthPerSqrtDay: Double = DEFAULT_RD_GROWTH,
        maxRatingDeviation: Double = MAX_RATING_DEVIATION,
    ): Double = min(maxRatingDeviation,
        sqrt(ratingDeviation * ratingDeviation + elapsedDays.coerceAtLeast(0.0) * growthPerSqrtDay * growthPerSqrtDay))

    fun update(
        player: GlickoRating,
        game: GlickoGame,
        maxRatingDeviation: Double = MAX_RATING_DEVIATION,
    ): GlickoUpdate {
        require(game.score in 0.0..1.0) { "Glicko score must be in [0, 1], was ${game.score}" }

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

        val newPhi = min(toPhi(maxRatingDeviation), 1.0 / sqrt(1.0 / phi / phi + 1.0 / variance))
        val normalizedRatingDelta = newPhi * newPhi * (opponentImpact * (game.score - expectedScore))
        check(normalizedRatingDelta.isFinite()) {
            "Glicko rating delta became non-finite for player=$player, game=$game"
        }

        val newMu = mu + normalizedRatingDelta
        return GlickoUpdate(
            rating = GlickoRating(
                rating = RATING_SCALE * newMu + 1500.0,
                ratingDeviation = newPhi * RATING_SCALE,
            ),
            normalizedRatingDelta = normalizedRatingDelta,
            expectedScore = expectedScore,
            ratingPeriodVariance = variance,
            opponentImpact = opponentImpact,
            estimatedImprovement = estimatedImprovement,
        )
    }

    private fun toPhi(ratingDeviation: Double): Double =
        ratingDeviation / RATING_SCALE
}
