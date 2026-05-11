package nl.korfbalelo.elo

import nl.korfbalelo.mijnkorfbal.Scraper
import nl.korfbalelo.mijnkorfbal.StaticPoules
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertTrue

class SeasonPredicterSpecialMatchExportTest {
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
    }

    @Test
    fun `kl exports special-only dates from related playoff results`() {
        val specialDate = LocalDate.of(2027, 4, 7)

        StaticPoules.loadIndoorPoules("zaal2627").forEach { (pouleName, pouleData) ->
            Scraper.indoorPoules[pouleName] = pouleData
        }

        Scraper.indoorPoules.values
            .flatMap { it.first.keys }
            .distinct()
            .forEach { teamName ->
                RankingNew.add(
                    Team(teamName, "test", 1500.0).apply {
                        lastDate = specialDate.minusDays(3)
                        rd = 120.0
                        averageScore = 15.0
                    }
                )
            }

        val specialMatch = Match("DeetosSnel", "HKC (Ha)", 26, 23, specialDate).also {
            it.special = true
        }
        Scraper.specialMatches[specialMatch.formatFixture()] = specialMatch
        SeasonPredicter.zeDate = specialDate

        val season = SeasonPredicter.IndoorSeason()

        assertTrue(season.korfbalLeague.matches.any { it.special && it.formatFixture() == specialMatch.formatFixture() })
        assertTrue(season.korfbalLeague2.matches.any { it.special && it.formatFixture() == specialMatch.formatFixture() })
        assertTrue(SeasonPredicter.shouldWritePouleSnapshot(season.korfbalLeague, specialDate))
        assertTrue(SeasonPredicter.shouldWritePouleSnapshot(season.korfbalLeague2, specialDate))
    }
}
