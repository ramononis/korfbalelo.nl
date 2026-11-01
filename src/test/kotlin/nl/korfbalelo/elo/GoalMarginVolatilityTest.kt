package nl.korfbalelo.elo

import org.apache.commons.math3.special.Erf
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// AI generated: likelihood-gradient, calibration and causal per-match behavior checks.
class GoalMarginVolatilityTest {
    private val config = VolatilityConfig(mode = VolatilityMode.GOAL_MARGIN,
        learningRate = 1.0, halfLifeDays = 0.0, maxLogVarianceStep = 100.0)
    private val h = GoalMarginVolatility.marginSensitivity(0.0, 6.0, 360.0)
    private val variance = 36.0 + h * h * 20000.0

    private fun next(margin: Double, q: Double = 700.0, c: VolatilityConfig = config) =
        GoalMarginVolatility.next(.06, margin, 0.0, 6.0, 100.0, 100.0, q, 360.0, c)

    @Test fun `unexpected large margin raises volatility even for equal-strength teams`() {
        assertTrue(next(2 * sqrt(variance)) > .06)
        assertTrue(next(0.0) < .06)
        assertEquals(.06, next(sqrt(variance)), 1e-12)
        assertEquals(next(12.0), next(-12.0), 1e-12)
    }

    @Test fun `log variance score has zero mean under its Gaussian predictive likelihood`() {
        // Three points integrate a quadratic score exactly: E[z^2] = 1.
        val score0 = 2 * ln(next(0.0) / .06)
        val scoreTail = 2 * ln(next(sqrt(3 * variance)) / .06)
        assertEquals(0.0, 2.0 / 3 * score0 + 1.0 / 3 * scoreTail, 1e-12)
    }

    @Test fun `update follows finite difference derivative of the predictive log likelihood`() {
        val q = 700.0
        val margin = 12.0
        fun likelihood(logMultiplier: Double): Double {
            val v = 36 + h * h * (20000 - q + q * exp(logMultiplier))
            return -.5 * ln(v) - margin * margin / (2 * v)
        }
        val epsilon = 1e-5
        val derivative = (likelihood(epsilon) - likelihood(-epsilon)) / (2 * epsilon)
        assertEquals(derivative, 2 * ln(next(margin, q) / .06), 1e-9)
    }

    @Test fun `margin sensitivity matches finite differences of the actual forecast mean`() {
        val sd = 6.0
        fun mean(gap: Double) = sd * sqrt(2.0) * Erf.erfInv(2 / (1 + 10.0.pow(-gap / 360)) - 1)
        for (gap in listOf(-600.0, 0.0, 400.0)) {
            val numerical = (mean(gap + .001) - mean(gap - .001)) / .002
            assertEquals(numerical, GoalMarginVolatility.marginSensitivity(mean(gap), sd, 360.0), 1e-8)
        }
    }

    @Test fun `shared mode stays fixed and zero process exposure cannot update sigma`() {
        assertEquals(.075, next(50.0, c = config.copy(mode = VolatilityMode.SHARED, initial = .075)))
        assertEquals(.06, next(50.0, q = 0.0))
        assertEquals(.075, GoalMarginVolatility.prepare(.04, 30.0,
            config.copy(mode = VolatilityMode.SHARED, initial = .075)))
    }

    @Test fun `calendar reversion forgets old sigma and exceptional scores remain bounded`() {
        val c = config.copy(halfLifeDays = 365.0)
        assertEquals(sqrt(.03 * .06), GoalMarginVolatility.prepare(.03, 365.0, c), 1e-12)
        val extreme = next(1e6, c = c.copy(learningRate = 256.0, maxLogVarianceStep = .5))
        assertTrue(extreme.isFinite() && extreme > .06 && extreme <= c.maximum)
    }

    @Test fun `both match sides use pre-match volatility and consume their process exposure`() {
        val oldModel = RatingModel.config
        val oldSeason = ScoreSeasonality.config
        val oldLog = ApplicationNew.log
        val oldHome = Team.H
        try {
            RatingModel.config = RatingModelConfig.standard.copy(initialHomeAdvantage = 0.0,
                homeAdvantageSpeed = 0.0, volatility = config)
            ScoreSeasonality.config = ScoreSeasonalityConfig.off
            ApplicationNew.log = false
            Team.reset()
            val date = LocalDate.of(2020, 1, 8)
            val home = Team("A", "A", 1500.0).also { it.rd = 100.0; it.lastDate = date.minusDays(7) }
            val away = Team("B", "B", 1500.0).also { it.rd = 100.0; it.lastDate = date.minusDays(7) }
            home.setNewRD(date)
            away.setNewRD(date)
            val match = Match("A", "B", 30, 0, date)
            val distribution = diffDistroBetween(home, away, date)
            val a = home.newRating(away, 30, 0, true, match, distribution)
            val b = away.newRating(home, 0, 30, false, match, -distribution.first to distribution.second)
            assertEquals(.06, home.rv)
            assertEquals(.06, away.rv)
            assertTrue(a.rv > .06)
            assertEquals(a.rv, b.rv, 1e-12)
            home.setNewRating(a)
            assertEquals(0.0, home.processVarianceSinceMatch)
            home.setNewRD(date)
            assertEquals(0.0, home.processVarianceSinceMatch)
        } finally {
            RatingModel.config = oldModel
            ScoreSeasonality.config = oldSeason
            ApplicationNew.log = oldLog
            Team.H = oldHome
        }
    }

}
