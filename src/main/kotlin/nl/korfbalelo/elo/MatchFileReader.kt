package nl.korfbalelo.elo

import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object MatchFileReader {
    fun readFile(file: File) = run {
        var lastDate = ""
        file.readLines().mapNotNull { lineComment ->
            try {
                val line = lineComment.replace(Regex("//.*"), "")
                if (!line.contains(Regex("\\w"))) return@mapNotNull null
                val (dateStr, t1, t2, s1, s2) = line.split("\t", ",").takeIf { it.size == 5 } ?: line.split(" - ")
                lastDate = dateStr.ifEmpty { lastDate }
                Match(t1, t2, s1.toInt(), s2.toInt(), LocalDate.parse(lastDate))
            } catch (ioebe: IndexOutOfBoundsException) {
                println(lineComment)
                throw ioebe
            }
        }
    }
}
