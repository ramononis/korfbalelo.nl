package nl.korfbalelo.elo

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplaceEventTest {
    @BeforeEach
    fun resetState() {
        ApplicationNew.log = false
        Team.reset()
        RankingNew.ranking.clear()
        RankingNew.aliases.clear()
        RankingNew.graph.clear()
        DiscontinuedTeams.clear()
        originMap = mutableMapOf()
    }

    @Test
    fun `replace resets non-rating stats but keeps rating state`() {
        val replaceDate = LocalDate.of(2026, 1, 15)
        val oldTeam = Team("Old Team", "City", Team.MAGIC_1500).also {
            it.rating = 1623.0
            it.rd = 93.0
            it.rv = 0.071
            it.games = 27
            it.averageScore = 17.8
            it.currentDiff = 42.0
            it.origins = 6
            it.startOffset = 123.0
            it.created = LocalDate.of(2020, 1, 1)
            it.lastDate = replaceDate
            it.opponents["Some Rival"] = 4 to 9.0
        }
        RankingNew.add(oldTeam)

        MergeEvent(
            date = replaceDate,
            name = "New Team",
            place = null,
            oldNames = setOf("Old Team"),
        ).run()

        val newTeam = RankingNew.ranking.getValue("New Team")
        assertFalse("Old Team" in RankingNew.ranking)

        assertEquals(1623.0, newTeam.rating)
        assertEquals(93.0, newTeam.rd)
        assertEquals(0.071, newTeam.rv)

        assertEquals(0, newTeam.games)
        assertEquals(5.0, newTeam.averageScore)
        assertEquals(0.0, newTeam.currentDiff)
        assertEquals(1, newTeam.origins)
        assertEquals(0.0, newTeam.startOffset)
        assertTrue(newTeam.opponents.isEmpty())
        assertNull(newTeam.lastDate)
        assertEquals(replaceDate, newTeam.created)

        val discontinued = DiscontinuedTeams.all()
        assertEquals(1, discontinued.size)
        assertEquals("Old Team", discontinued.first().name)
        assertEquals("Hernoemd naar New Team", discontinued.first().fate)
        assertEquals(27, discontinued.first().matches)
    }

    @Test
    fun `replace gets a fresh graph series when label already exists`() {
        val replaceDate = LocalDate.of(2026, 2, 1)
        val oldTeam = Team("Old Team", "City", Team.MAGIC_1500).also {
            it.rating = 1510.0
            it.rd = 80.0
            it.rv = 0.06
            it.lastDate = replaceDate
        }
        RankingNew.add(oldTeam)

        ApplicationNew.log = true
        RankingNew.graph.addRanking(replaceDate.minusDays(1), "New Team (City)", 1500)
        ApplicationNew.log = false

        MergeEvent(
            date = replaceDate,
            name = "New Team",
            place = null,
            oldNames = setOf("Old Team"),
        ).run()

        val newTeam = RankingNew.ranking.getValue("New Team")
        assertEquals("New Team (City) [r:$replaceDate]", newTeam.graphName)
        assertTrue(newTeam.graphName != newTeam.fullName)
    }

    @Test
    fun `merge to new name resets stats and does not rename old team object`() {
        val mergeDate = LocalDate.of(2026, 3, 10)
        val oldTeam = Team("Old Team", "City", Team.MAGIC_1500).also {
            it.rating = 1540.0
            it.rd = 91.0
            it.rv = 0.062
            it.games = 88
            it.averageScore = 14.2
            it.currentDiff = -7.0
            it.origins = 4
            it.lastDate = mergeDate
        }
        RankingNew.add(oldTeam)

        MergeEvent(
            date = mergeDate,
            name = "New Team",
            place = null,
            oldNames = setOf("Old Team"),
        ).run()

        val newTeam = RankingNew.ranking.getValue("New Team")
        assertEquals("Old Team", oldTeam.name)
        assertEquals(0, newTeam.games)
        assertEquals(5.0, newTeam.averageScore)
        assertEquals(1, newTeam.origins)
        assertNull(newTeam.lastDate)
    }

    @Test
    fun `merge keeps stats only when resulting name is one of old teams`() {
        val mergeDate = LocalDate.of(2026, 4, 5)
        val keepTeam = Team("Keep Me", "City", Team.MAGIC_1500).also {
            it.rating = 1510.0
            it.rd = 100.0
            it.rv = 0.061
            it.games = 42
            it.averageScore = 13.1
            it.currentDiff = 3.0
            it.origins = 2
            it.created = LocalDate.of(2020, 1, 1)
            it.lastDate = mergeDate.minusDays(1)
        }
        val otherTeam = Team("Other Team", "City", Team.MAGIC_1500).also {
            it.rating = 1660.0
            it.rd = 80.0
            it.rv = 0.07
            it.games = 9
            it.averageScore = 20.0
            it.lastDate = mergeDate.minusDays(1)
        }
        RankingNew.add(keepTeam)
        RankingNew.add(otherTeam)

        MergeEvent(
            date = mergeDate,
            name = "Keep Me",
            place = null,
            oldNames = listOf("Keep Me", "Other Team"),
        ).run()

        val merged = RankingNew.ranking.getValue("Keep Me")
        assertTrue(merged === keepTeam)
        assertFalse("Other Team" in RankingNew.ranking)
        assertEquals(42, merged.games)
        assertEquals(13.1, merged.averageScore)
        assertEquals(2, merged.origins)
        assertEquals(LocalDate.of(2020, 1, 1), merged.created)
        assertEquals(1660.0, merged.rating)
        assertEquals(otherTeam.rd, merged.rd)
        assertEquals((0.061 + 0.07) / 2.0, merged.rv)

        val discontinued = DiscontinuedTeams.all()
        assertEquals(1, discontinued.size)
        assertEquals("Other Team", discontinued.first().name)
        assertEquals("Opgegaan in Keep Me", discontinued.first().fate)
    }

    @Test
    fun `parse merge marks keep-stats by resulting team membership`() {
        val toNew = parseEvent("2026-01-01 merge Old Team > New Team > City") as MergeEvent
        assertFalse(toNew.name in toNew.oldNames)

        val keepExisting = parseEvent("2026-01-01 merge Old Team,Other Team > Old Team > City") as MergeEvent
        assertTrue(keepExisting.name in keepExisting.oldNames)
    }
}
