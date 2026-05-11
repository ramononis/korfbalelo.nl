package nl.korfbalelo.elo

import nl.korfbalelo.elo.ApplicationNew.events
import java.io.File
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object AccuracyTracker {
    val splitDate = LocalDate.of(1946,9,1)
//        val splitDate = LocalDate.of(2025, 8, 1)
    val trackStartDate = LocalDate.of(2025, 8, 1)
//    val trackStartDate = LocalDate.of(2024, 8, 1)

    var ignoreMatchesBelow = LocalDate.of(1900, 1, 1)

    val accuracyRange = LocalDate.of(2000, 8, 1) to LocalDate.of(2024, 8, 20)
//    val accuracyRange = LocalDate.of(2023, 9, 1) to LocalDate.of(2024, 9, 1)

    @JvmStatic
    fun main(args: Array<String>) {
        val file = File("accuracy.csv")
        ignoreMatchesBelow = accuracyRange.first
        ApplicationNew.log = false
        ApplicationNew.main(emptyArray())

        val record = PredictionBenchmark.accuracy.summary().weightedMarginAccuracy
        println("Start score = ${record.formatPct()}")
        file.writeText("date,score\n${accuracyRange.first},$record\n")
        events.map { it.date.with(TemporalAdjusters.firstDayOfNextMonth()) }.toSet().filter { it < accuracyRange.first }.sortedDescending().forEach { ignoreBelow ->
            ignoreMatchesBelow = ignoreBelow
            ApplicationNew.main(emptyArray())
            file.appendText("$ignoreBelow,${PredictionBenchmark.accuracy.summary().weightedMarginAccuracy}\n")
        }
    }
}
