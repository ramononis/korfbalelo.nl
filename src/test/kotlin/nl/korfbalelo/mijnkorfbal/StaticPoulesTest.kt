package nl.korfbalelo.mijnkorfbal

import nl.korfbalelo.elo.RankingNew
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticPoulesTest {
    @BeforeEach
    fun resetRanking() {
        RankingNew.ranking.clear()
    }

    @Test
    fun `loads zaal 2627 first-team poules from static source`() {
        val poules = StaticPoules.loadIndoorPoules("zaal2627")

        assertEquals(34, poules.size)
        assertEquals(10, poules.getValue("KL").first.size)
        assertEquals(8, poules.getValue("3J").first.size)
        assertEquals(10, poules.keys.count { it.startsWith("3") })

        val allTeams = poules.values.flatMap { it.first.keys }
        assertEquals(allTeams.size, allTeams.toSet().size)
        assertTrue("Noviomagum" in poules.getValue("1D").first)
        assertTrue("SCO" in poules.getValue("OKA").first)
        assertTrue("'t Capproen" in poules.getValue("3I").first)
        assertTrue("Duko" in poules.getValue("2D").first)
        assertFalse("Keizer Karel" in allTeams)
        assertFalse("WKS" in allTeams)
    }

    @Test
    fun `orders static zaal teams by current ranking within each poule`() {
        val ratings = loadRatings()
        val poules = StaticPoules.loadIndoorPoules("zaal2627")

        poules.values.forEach { (teams) ->
            val teamNames = teams.keys.toList()
            val expectedOrder = teamNames.sortedWith(
                compareByDescending<String> { ratings[it] ?: 1500.0 }
                    .thenBy { it }
            )
            assertEquals(expectedOrder, teamNames)
        }
    }

    private fun loadRatings(): Map<String, Double> =
        File("web/public/ranking.csv")
            .readLines()
            .mapNotNull { line ->
                val fields = parseCsvLine(line)
                if (fields.size < 2) null else fields[0] to fields[1].toDouble()
            }
            .toMap()

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && line.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }

                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(field.toString())
                    field.clear()
                }

                else -> field.append(char)
            }
            index++
        }
        result.add(field.toString())
        return result
    }
}
