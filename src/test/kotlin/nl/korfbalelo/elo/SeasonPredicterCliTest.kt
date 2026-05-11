package nl.korfbalelo.elo

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeasonPredicterCliTest {
    @Test
    fun `requested start date uses first arg for outdoor runs`() {
        assertEquals(
            LocalDate.of(2026, 4, 9),
            SeasonPredicter.requestedStartDate(arrayOf("2026-04-09", "veld")),
        )
    }

    @Test
    fun `requested start date uses first arg for indoor runs`() {
        assertEquals(
            LocalDate.of(2026, 4, 9),
            SeasonPredicter.requestedStartDate(arrayOf("2026-04-09", "zaal")),
        )
    }

    @Test
    fun `requested start date is absent without explicit date`() {
        assertNull(SeasonPredicter.requestedStartDate(emptyArray()))
    }
}
