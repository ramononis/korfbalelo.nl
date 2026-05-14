package nl.korfbalelo.elo.graph

import nl.korfbalelo.elo.ApplicationNew
import java.time.LocalDate
import java.io.File
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.io.FileWriter
import java.time.format.DateTimeFormatter
import java.io.IOException
import java.util.*
import kotlin.math.max

class DyGraph {
    private val entries: SortedMap<LocalDate, GraphEntry> = TreeMap()
    private val teams: MutableSet<String> = HashSet()
    fun addRanking(ld: LocalDate, team: String, ranking: Int) {
        if (!ApplicationNew.log) return
        val monday = if (ld.year >= 2024) ld.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else ld.with(TemporalAdjusters.firstDayOfNextMonth())
        teams.add(team)
        entries.computeIfAbsent(monday) { GraphEntry() }.rating.compute(team) { _, r -> r?.let{ max(it,ranking) } ?: ranking }
    }

    fun hasTeam(team: String): Boolean = team in teams

    fun clear() {
        entries.clear()
        teams.clear()
    }

    fun writeCsv(file: File?) {
        if (!ApplicationNew.log) return
        try {
            FileWriter(file, false).use { fw ->
                val sortedTeams = teams.sorted()
                fw.write("Date,")
                fw.write(java.lang.String.join(",", sortedTeams))
                fw.write("\n")
                entries.forEach { (e: LocalDate, k: GraphEntry) ->
                    try {
                        fw.write(e.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + ",")
                        fw.write(sortedTeams.joinToString(",") { t: String -> k.rating[t]?.toString() ?: "" })
                        fw.write("\n")
                    } catch (ex: IOException) {
                        ex.printStackTrace()
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private class GraphEntry {
        val rating = HashMap<String, Int>()
    }
}
