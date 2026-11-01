package nl.korfbalelo.elo

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// AI generated: shared process noise and a sequential Gaussian predictive-likelihood score.
enum class VolatilityMode { LEGACY, SHARED, GOAL_MARGIN }

data class VolatilityConfig(
    val mode: VolatilityMode = VolatilityMode.LEGACY,
    val initial: Double = 0.06,
    val learningRate: Double = 32.0,
    val halfLifeDays: Double = 730.0,
    val minimum: Double = 0.015,
    val maximum: Double = 0.20,
    val maxLogVarianceStep: Double = 0.5,
) {
    init {
        require(initial.isFinite() && initial > 0)
        require(minimum > 0 && maximum >= initial && minimum <= initial && maximum.isFinite())
        require(learningRate.isFinite() && learningRate >= 0)
        require(halfLifeDays.isFinite() && halfLifeDays >= 0)
        require(maxLogVarianceStep.isFinite() && maxLogVarianceStep > 0)
    }

    companion object {
        fun fromSystemProperties(defaults: VolatilityConfig = VolatilityConfig()): VolatilityConfig = defaults.copy(
            mode = System.getProperty("elo.model.volatility.mode")?.let { VolatilityMode.valueOf(it.uppercase()) }
                ?: defaults.mode,
            initial = System.getProperty("elo.model.volatility.initial")?.toDouble() ?: defaults.initial,
            learningRate = System.getProperty("elo.model.volatility.learningRate")?.toDouble() ?: defaults.learningRate,
            halfLifeDays = System.getProperty("elo.model.volatility.halfLifeDays")?.toDouble() ?: defaults.halfLifeDays,
        )
    }
}

object GoalMarginVolatility {
    fun prepare(sigma: Double, elapsedDays: Double, config: VolatilityConfig): Double = when (config.mode) {
        VolatilityMode.LEGACY -> sigma
        VolatilityMode.SHARED -> config.initial
        VolatilityMode.GOAL_MARGIN -> {
            val retention = if (config.halfLifeDays > 0) 0.5.pow(elapsedDays / config.halfLifeDays) else 1.0
            exp(ln(config.initial) + retention * (ln(sigma) - ln(config.initial)))
                .coerceIn(config.minimum, config.maximum)
        }
    }

    // Local linearization of meanMargin = goalSd * inverseNormalCDF(logistic(ratingGap)).
    fun marginSensitivity(meanMargin: Double, goalSd: Double, ratingScale: Double): Double {
        val z = meanMargin / goalSd
        val p = ND(0.0, 1.0).cdf(z).coerceIn(1e-12, 1.0 - 1e-12)
        val density = exp(-z * z / 2) / sqrt(2 * PI)
        return goalSd * ln(10.0) / ratingScale * p * (1 - p) / density.coerceAtLeast(1e-15)
    }

    fun next(
        sigma: Double,
        actualMargin: Double,
        meanMargin: Double,
        goalSd: Double,
        ownRd: Double,
        opponentRd: Double,
        ownProcessVariance: Double,
        ratingScale: Double,
        config: VolatilityConfig,
    ): Double {
        if (config.mode == VolatilityMode.SHARED) return config.initial
        if (config.mode == VolatilityMode.LEGACY || ownProcessVariance <= 0) return sigma
        val h = marginSensitivity(meanMargin, goalSd, ratingScale)
        val variance = goalSd * goalSd + h * h * (ownRd * ownRd + opponentRd * opponentRd)
        val elasticity = h * h * ownProcessVariance / variance
        // AI generated: local score for log process variance. At an RD cap, the effective
        // variance increment is treated as locally scalable (a surrogate, not a full filter derivative).
        // d log N(actualMargin | meanMargin, variance) / d log(sigma^2).
        val score = 0.5 * elasticity * ((actualMargin - meanMargin).pow(2) / variance - 1)
        val step = (config.learningRate * score).coerceIn(-config.maxLogVarianceStep, config.maxLogVarianceStep)
        return exp(ln(sigma) + step / 2).coerceIn(config.minimum, config.maximum)
    }
}
