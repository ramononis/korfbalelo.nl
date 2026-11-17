package nl.korfbalelo.elo

import java.time.LocalDate
import kotlin.math.pow

// AI generated: forecast-only scoring context; pending results exclude same-day leakage.
class SeasonScoring(val state: Array<DoubleArray> = Array(2) { doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, -1000000.0) }) {
    @Transient private val cache = java.util.concurrent.ConcurrentHashMap<LocalDate, Double>()
    fun value(date: LocalDate): Double = cache.computeIfAbsent(date, ::calculate)
    private fun calculate(date: LocalDate): Double {
        val s = state[phase(date)]
        val day = date.toEpochDay().toDouble()
        val decay = 0.5.pow((day - s[2]).coerceAtLeast(0.0) / 180.0)
        val pendingDecay = if (day > s[5]) 0.5.pow((day - s[5]) / 180.0) else 0.0
        return (s[0] * decay + s[3] * pendingDecay) / (1.0 + s[1] * decay + s[4] * pendingDecay)
    }

    fun observe(date: LocalDate, residual: Double) {
        cache.clear()
        val s = state[phase(date)]
        val day = date.toEpochDay().toDouble()
        require(day >= s[5]) { "Scoring history must be chronological" }
        if (day > s[5]) {
            val decay = 0.5.pow((day - s[2]).coerceAtLeast(0.0) / 180.0)
            val pendingDecay = if (s[4] > 0) 0.5.pow((day - s[5]) / 180.0) else 0.0
            s[0] = s[0] * decay + s[3] * pendingDecay
            s[1] = s[1] * decay + s[4] * pendingDecay
            s[2] = day
            s[3] = 0.0; s[4] = 0.0; s[5] = day
        }
        s[3] += residual.coerceIn(-3.0, 3.0)
        s[4] += 1.0
    }

    companion object {
        fun phase(date: LocalDate) = if (date.monthValue in 4..10) 0 else 1
        fun enabled() = System.getProperty("elo.model.seasonScoring", "true").toBoolean()
        fun correction(home: Team, away: Team, date: LocalDate?, sd: Double): Double =
            if (date == null || !enabled()) 0.0 else
                (0.75 * sd * (home.seasonScoring.value(date) + away.seasonScoring.value(date)) / 2).coerceIn(-3.0, 3.0)
    }
}
