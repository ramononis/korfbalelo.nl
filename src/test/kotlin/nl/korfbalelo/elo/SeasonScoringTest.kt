package nl.korfbalelo.elo

import java.time.LocalDate
import kotlin.test.*
import org.junit.jupiter.api.Test

// AI generated: causal history and portable-state contracts.
class SeasonScoringTest {
    private val d = LocalDate.of(2026, 11, 1)
    @Test fun `pending games are excluded today and included tomorrow`() {
        val f = SeasonScoring()
        f.observe(d, 2.0); f.observe(d, 2.0)
        assertEquals(0.0, f.value(d))
        val decay = Math.pow(0.5, 1.0 / 180)
        assertEquals(4 * decay / (1 + 2 * decay), f.value(d.plusDays(1)), 1e-12)
        f.observe(d.plusDays(1), -1.0)
        assertEquals(4 * decay / (1 + 2 * decay), f.value(d.plusDays(1)), 1e-12)
    }
    @Test fun `queries do not age stored history and other context is independent`() {
        val f = SeasonScoring(); f.observe(d, 2.0)
        val tomorrow = f.value(d.plusDays(1))
        f.value(d.plusDays(3650))
        assertEquals(tomorrow, f.value(d.plusDays(1)))
        assertEquals(0.0, f.value(LocalDate.of(2027, 4, 1)))
    }
    @Test fun `serialized state preserves pending history and correction caps`() {
        val f = SeasonScoring(); f.observe(d, 100.0)
        assertFalse(gson.toJson(f).contains("cache"))
        val copy = SeasonScoring(gson.fromJson(gson.toJson(f.state), Array<DoubleArray>::class.java))
        assertEquals(f.value(d.plusDays(1)), copy.value(d.plusDays(1)))
        val h = Team("h", "", 1500.0).apply { seasonScoring = f }
        val a = Team("a", "", 1500.0).apply { seasonScoring = copy }
        assertEquals(3.0, SeasonScoring.correction(h, a, d.plusDays(1), 10.0))
    }
    @Test fun `score correction shifts both means without changing margin or variance`() {
        Team.reset()
        val h = Team("h", "", 1500.0).apply { averageScore = 20.0 }
        val a = Team("a", "", 1450.0).apply { averageScore = 18.0 }
        val target = d.plusDays(1)
        val before = distroBetween(h, a, date = target)
        h.seasonScoring.observe(d, 1.0)
        a.seasonScoring.observe(d, 2.0)
        val after = distroBetween(h, a, date = target)
        assertEquals(before.first.second, after.first.second)
        assertEquals(before.second.second, after.second.second)
        assertEquals(before.first.first - before.second.first, after.first.first - after.second.first, 1e-12)
        assertTrue(after.first.first > before.first.first)
    }

}
