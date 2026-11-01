package nl.korfbalelo.elo

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// AI generated: independent numerical fixtures distinguish model behavior from solver errors.
class VolatilityInvestigationTest {
    private val player = Glicko2Rating(1500.0, 100.0, 0.06)

    @Test
    fun `every single result against an equally rated opponent lowers volatility`() {
        for (score in listOf(0.0, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0)) {
            val result = Glicko2Calculator.update(player, Glicko2Game(player, score))
            assertTrue(result.rating.volatility < player.volatility, "score=$score")
        }
    }

    @Test
    fun `volatility agrees with independent bisection fixtures`() {
        // Independently solved f(log(sigma^2))=0 with tau=0.6; no extra RD inflation.
        val win = Glicko2Calculator.update(player, Glicko2Game(player, 1.0))
        val draw = Glicko2Calculator.update(player, Glicko2Game(player, 0.5))
        val upset = Glicko2Calculator.update(player,
            Glicko2Game(Glicko2Rating(1900.0, 50.0, 0.06), 1.0))
        // The solver stops at 1e-6 in log variance: about 3e-8 on sigma near 0.06.
        assertEquals(0.059999709911974834, win.rating.volatility, 3.1e-8)
        assertEquals(0.05999589758652276, draw.rating.volatility, 3.1e-8)
        assertEquals(0.06001320875510931, upset.rating.volatility, 3.1e-8)
        assertTrue(upset.rating.volatility > player.volatility)
        assertTrue(draw.rating.volatility < win.rating.volatility)
    }

    @Test
    fun `volatility root agrees with an independent bisection across varied matchups`() {
        val random = Random(20261101)
        repeat(200) {
            val own = Glicko2Rating(random.nextDouble(1000.0, 2500.0),
                random.nextDouble(20.0, 350.0), random.nextDouble(0.04, 0.08))
            val other = Glicko2Rating(random.nextDouble(1000.0, 2500.0),
                random.nextDouble(20.0, 350.0), 0.06)
            val result = Glicko2Calculator.update(own, Glicko2Game(other, random.nextDouble()))
            val phi2 = (own.ratingDeviation / Glicko2Calculator.RATING_SCALE).pow(2)
            val v = result.ratingPeriodVariance
            val delta2 = result.estimatedImprovement.pow(2)
            val a = ln(own.volatility.pow(2))
            fun objective(x: Double): Double {
                val q = exp(x)
                return q * (delta2 - phi2 - v - q) / (2 * (phi2 + v + q).pow(2)) - (x - a) / 0.36
            }
            var lo = a - 20.0
            var hi = a + 20.0
            assertTrue(objective(lo) > 0 && objective(hi) < 0)
            repeat(150) {
                val mid = (lo + hi) / 2
                if (objective(mid) > 0) lo = mid else hi = mid
            }
            assertTrue(abs(ln(result.rating.volatility.pow(2)) - (lo + hi) / 2) < 1.1e-6)
        }
    }

    @Test
    fun `calendar aging accumulates variance additively`() {
        val once = Glicko2Calculator.ageRatingDeviation(100.0, 0.06, 30.0)
        val split = Glicko2Calculator.ageRatingDeviation(
            Glicko2Calculator.ageRatingDeviation(100.0, 0.06, 10.0), 0.06, 20.0)
        assertEquals(once, split, 1e-12)
    }
}
