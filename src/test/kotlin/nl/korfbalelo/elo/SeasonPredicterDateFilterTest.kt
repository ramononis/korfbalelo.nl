package nl.korfbalelo.elo

import nl.korfbalelo.mijnkorfbal.Scraper
import nl.korfbalelo.mijnkorfbal.StaticPoules
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeasonPredicterDateFilterTest {
    @BeforeEach
    fun resetState() {
        ApplicationNew.log = false
        ApplicationNew.forceOutput = false
        ApplicationNew.matches.clear()
        Team.reset()
        RankingNew.ranking.clear()
        RankingNew.aliases.clear()
        RankingNew.graph.clear()
        DiscontinuedTeams.clear()
        originMap = mutableMapOf()
        Scraper.outdoorPoules.clear()
        Scraper.indoorPoules.clear()
        Scraper.specialMatches.clear()
        Scraper.activeTeams.clear()
        PoulePredicter.reset()
        SeasonPredicter.doOutdoor = false
        SeasonPredicter.zeDate = null
        SeasonPredicter.count.set(0)
        SeasonPredicter.events.clear()
    }

    @Test
    fun `snapshot work only runs on relevant indoor dates`() {
        val matchDate = LocalDate.of(2027, 4, 11)
        val unrelatedDate = LocalDate.of(2027, 4, 16)

        StaticPoules.loadIndoorPoules("zaal2627").forEach { (pouleName, pouleData) ->
            Scraper.indoorPoules[pouleName] = pouleData
        }

        Scraper.indoorPoules.values
            .flatMap { it.first.keys }
            .distinct()
            .forEach { teamName ->
                RankingNew.add(
                    Team(teamName, "test", 1500.0).apply {
                        lastDate = matchDate
                        rd = 120.0
                        averageScore = 15.0
                    }
                )
            }

        SeasonPredicter.zeDate = matchDate
        assertTrue(SeasonPredicter.hasSnapshotWork(matchDate))

        SeasonPredicter.zeDate = unrelatedDate
        assertFalse(SeasonPredicter.hasSnapshotWork(unrelatedDate))
    }
}
