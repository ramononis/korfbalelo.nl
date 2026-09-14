package nl.korfbalelo.elo

import nl.korfbalelo.elo.application.DeclarativeSeasonTransitionSimulator
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutdoorSeasonOutcomeSemanticsTest {
    private val simulator =
        DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/veld2526vj__veld2627nj.json"))
    private val activeSimulator =
        DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/veld2627nj__veld2627vj.json"))

    @Test
    fun `veld ereklasse bars treat playoffs as green and ereklasse d as same-tier`() {
        assertTrue(simulator.seasonOutcomePromote("ek", "EK-01", 1, 4, "ek"))
        assertFalse(simulator.seasonOutcomePromote("ek", "EK-01", 2, 4, "ek"))
        assertFalse(simulator.seasonOutcomePromote("ekd", "EK-D-01", 1, 4, "ek"))
        assertFalse(simulator.seasonOutcomePromote("ekd", "EK-D-01", 2, 4, "ek"))
        assertTrue(simulator.seasonOutcomeRelegate("ekd", 3, 4, "hk"))
    }

    @Test
    fun `active veld ereklasse uses promotion same-tier and relegation outcomes`() {
        assertTrue(activeSimulator.seasonOutcomePromote("ek", "EK-01", 1, 4, "ek"))
        assertFalse(activeSimulator.seasonOutcomeRelegate("ek", 1, 4, "ek"))
        assertTrue(activeSimulator.seasonOutcomePromote("ek", "EK-01", 2, 4, "ek"))
        assertFalse(activeSimulator.seasonOutcomeRelegate("ek", 2, 4, "ek"))
        assertFalse(activeSimulator.seasonOutcomePromote("ek", "EK-01", 3, 4, "ekd"))
        assertFalse(activeSimulator.seasonOutcomeRelegate("ek", 3, 4, "ekd"))
        assertFalse(activeSimulator.seasonOutcomePromote("ek", "EK-01", 4, 4, "hk"))
        assertTrue(activeSimulator.seasonOutcomeRelegate("ek", 4, 4, "hk"))
    }
}
