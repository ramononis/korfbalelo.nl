package nl.korfbalelo.elo

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeasonPredicterPoolPatternTest {
    @Test
    fun `kl championship patterns include eindfase`() {
        assertTrue(SeasonPredicter.klChampionshipPoolPatterns.any { it.matches("KL(2) Eindfase") })
    }

    @Test
    fun `kl promotion patterns exclude kl eindfase`() {
        assertFalse(SeasonPredicter.klPromotionPoolPatterns.any { it.matches("KL(2) Eindfase") })
    }
}
