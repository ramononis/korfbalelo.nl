package nl.korfbalelo.elo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeasonPredicterSnapshotHeaderTest {
    @Test
    fun `keeps existing snapshot header order when it contains the same teams`() {
        val existingOrder = listOf("DKOD", "KCD", "Olympia '22")
        val currentOrder = listOf("KCD", "Olympia '22", "DKOD")
        val header = listOf("date", "pouleData") + existingOrder.flatMap { team -> List(5) { team } }

        assertEquals(existingOrder, SeasonPredicter.compatibleSnapshotHeaderTeamOrder(header, currentOrder))
    }

    @Test
    fun `rejects snapshot header order when team set changed`() {
        val header = listOf("date", "pouleData") + listOf("DKOD", "KCD").flatMap { team -> List(5) { team } }

        assertNull(SeasonPredicter.compatibleSnapshotHeaderTeamOrder(header, listOf("DKOD", "KCD", "Regio '72")))
    }
}
