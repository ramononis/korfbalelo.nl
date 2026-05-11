package nl.korfbalelo.mijnkorfbal

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScraperPoolNameTest {
    @Test
    fun `treats eindfase pools as postseason`() {
        assertTrue(Scraper.isPostSeasonPool("KL(2) Eindfase"))
    }

    @Test
    fun `does not treat regular poules as postseason`() {
        assertFalse(Scraper.isPostSeasonPool("KL"))
    }
}
