package nl.korfbalelo.elo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// AI generated: profiles configure all three learning components and honor explicit overrides.
class RatingProfilesTest {
    @Test fun `calibrated profiles configure rating score and seasonal learning together`() {
        val keys = listOf("elo.model.profile", "elo.model.volatility.initial", "elo.model.marginRatingScale")
        val previous = keys.associateWith(System::getProperty)
        try {
            System.clearProperty("elo.model.volatility.initial")
            System.clearProperty("elo.model.marginRatingScale")
            System.setProperty("elo.model.profile", "shared-balanced")
            val model = RatingModelConfig.fromSystemProperties()
            assertEquals(VolatilityMode.SHARED, model.volatility.mode)
            assertEquals(.066, model.volatility.initial)
            assertEquals(.136, model.scoreSdSlope)
            assertEquals(0.0, model.homeAdvantageSpeed)
            assertEquals(.005, ScoreSeasonalityConfig.fromSystemProperties().learningRate)
            assertEquals(.001, ScoreRatingTweakConfig.fromSystemProperties().learningRate)
            System.setProperty("elo.model.profile", "shared-matched")
            assertEquals(VolatilityMode.SHARED, RatingModelConfig.fromSystemProperties().volatility.mode)
            assertEquals(.06, RatingModelConfig.fromSystemProperties().volatility.initial)
            assertEquals(450.0, RatingModelConfig.fromSystemProperties().rdMax)
            System.setProperty("elo.model.profile", "goal-margin-balanced")
            assertEquals(VolatilityMode.GOAL_MARGIN, RatingModelConfig.fromSystemProperties().volatility.mode)
            assertEquals(.75, RatingModelConfig.fromSystemProperties().initialRdFraction)
            System.setProperty("elo.model.volatility.initial", ".07")
            System.setProperty("elo.model.marginRatingScale", "380")
            assertEquals(.07, RatingModelConfig.fromSystemProperties().volatility.initial)
            assertEquals(380.0, RatingModelConfig.fromSystemProperties().marginRatingScale)
            System.setProperty("elo.model.profile", "unknown")
            assertFailsWith<IllegalArgumentException> { RatingModelConfig.fromSystemProperties() }
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
        }
    }
}
