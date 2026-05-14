package nl.korfbalelo.elo.graph

import nl.korfbalelo.elo.ApplicationNew
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals

class DyGraphTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `writes teams in stable sorted order`() {
        ApplicationNew.log = true
        val graph = DyGraph()
        graph.addRanking(LocalDate.of(2024, 1, 1), "Team B", 1600)
        graph.addRanking(LocalDate.of(2024, 1, 1), "Team A", 1500)

        val output = File(tempDir, "graph.csv")
        graph.writeCsv(output)

        assertEquals("Date,Team A,Team B", output.readLines().first())
    }
}
