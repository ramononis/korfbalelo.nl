package nl.korfbalelo.elo

import nl.korfbalelo.elo.application.DeclarativeSeasonTransitionSimulator
import org.junit.jupiter.api.Test
import java.io.File
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

    @Test
    fun `autumn EK winner is champion while spring EK winner enters championship playoffs`() {
        val autumn = DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/veld2627nj__veld2627vj.json"))
        val spring = DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/veld2526vj__veld2627nj.json"))

        assertTrue(autumn.automaticChampionApplies("EK-01", 1))
        assertFalse(autumn.automaticChampionApplies("EK-01", 2))
        assertFalse(spring.automaticChampionApplies("EK-01", 1))
    }
}
