package nl.korfbalelo.elo.infrastructure

import nl.korfbalelo.elo.Match
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class FileEventCatalogTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `loads events in stable source order`() {
        val matchesDir = File(tempDir, "matches").also(File::mkdir)
        File(matchesDir, "2024.csv").writeText(
            """
            2024-01-02,Historical C,Historical D,9,8
            2024-01-03,Historical E,Historical F,7,7
            """.trimIndent(),
        )
        File(matchesDir, "1903.csv").writeText("1903-01-01,Historical A,Historical B,1,0")

        val commandsFile = File(tempDir, "club_events.txt").also { it.writeText("") }
        val currentMatchesFile =
            File(tempDir, "current.txt").also { it.writeText("2026-05-11,Current A,Current B,3,3") }

        val catalog = FileEventCatalog(
            matchesDirectory = matchesDir,
            commandsFile = commandsFile,
            currentMatchesFile = currentMatchesFile,
        )

        assertEquals(
            listOf(
                "2026-05-11,Current A,Current B,3,3",
                "1903-01-01,Historical A,Historical B,1,0",
                "2024-01-02,Historical C,Historical D,9,8",
                "2024-01-03,Historical E,Historical F,7,7",
            ),
            catalog.loadAllEvents().filterIsInstance<Match>().map(Match::format),
        )
    }
}
