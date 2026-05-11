package nl.korfbalelo.elo

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `future requested date forces a fallback snapshot`() {
        assertTrue(
            SeasonPredicter.shouldForceFutureSnapshot(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2026, 5, 11),
            ),
        )
        assertFalse(
            SeasonPredicter.shouldForceFutureSnapshot(
                LocalDate.of(2026, 5, 11),
                LocalDate.of(2026, 5, 11),
            ),
        )
    }
}
