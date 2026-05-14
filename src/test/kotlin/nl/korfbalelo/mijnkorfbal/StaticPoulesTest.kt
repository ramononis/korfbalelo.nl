package nl.korfbalelo.mijnkorfbal

import nl.korfbalelo.elo.RankingNew
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticPoulesTest {
    @TempDir
    lateinit var tempDir: File

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
    fun `preserves authored static zaal team order within each poule`() {
        val poules = StaticPoules.loadIndoorPoules("zaal2627")

        assertEquals(
            listOf("Fortuna (D)", "Unitas", "DVO", "DOS '46", "TOP (S)", "Dalto", "LDODK", "DeetosSnel", "KZ", "PKC"),
            poules.getValue("KL").first.keys.toList(),
        )
        assertEquals(
            listOf("Noviomagum", "Woudenberg", "Rust Roest (E)", "Victum", "Tiel '72", "Animo (G)", "Viking", "SDO (V)"),
            poules.getValue("1D").first.keys.toList(),
        )
    }

    @Test
    fun `does not overwrite existing static snapshot csvs by default`() {
        val publicDirectory = File(tempDir, "public")
        val snapshotDirectory = File(publicDirectory, "csv/zaal2627").also(File::mkdirs)
        val existingSnapshot = File(snapshotDirectory, "KL.csv").also {
            it.writeText("existing simulated probabilities\n")
        }

        StaticPoules.writeFrontendArtifacts("zaal2627", publicDirectory = publicDirectory)

        assertEquals("existing simulated probabilities\n", existingSnapshot.readText())
        assertTrue(File(publicDirectory, "zaal2627.json").exists())
        assertTrue(File(snapshotDirectory, "1A.csv").exists())
    }
}
