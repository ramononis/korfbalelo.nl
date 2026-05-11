package nl.korfbalelo.elo

import java.time.LocalDate

// AI generated
object BenchmarkWindow {
    fun fromEvents(events: Collection<RankingEvent>): Pair<LocalDate, LocalDate> {
        val from = System.getProperty("elo.benchmark.from")?.let(LocalDate::parse)
            ?: events.minOf(RankingEvent::date)
        val to = System.getProperty("elo.benchmark.to")?.let(LocalDate::parse)
            ?: events.maxOf(RankingEvent::date)
        require(!from.isAfter(to)) { "Invalid benchmark window: $from..$to" }
        return from to to
    }
}
