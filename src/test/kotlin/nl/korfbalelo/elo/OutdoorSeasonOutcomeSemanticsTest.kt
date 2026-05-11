package nl.korfbalelo.elo

import nl.korfbalelo.elo.application.DeclarativeSeasonTransitionSimulator
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutdoorSeasonOutcomeSemanticsTest {
    private val simulator =
        DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/veld2526vj__veld2627nj.json"))

    @Test
    fun `veld ereklasse bars treat playoffs as green and ereklasse d as same-tier`() {
        assertTrue(simulator.seasonOutcomePromote("ek", "EK-01", 1, "ek"))
        assertFalse(simulator.seasonOutcomePromote("ek", "EK-01", 2, "ek"))
        assertFalse(simulator.seasonOutcomePromote("ekd", "EK-D-01", 1, "ek"))
        assertFalse(simulator.seasonOutcomePromote("ekd", "EK-D-01", 2, "ek"))
        assertTrue(simulator.seasonOutcomeRelegate("ekd", "hk"))
    }
}
