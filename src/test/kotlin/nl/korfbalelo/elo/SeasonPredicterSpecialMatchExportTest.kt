package nl.korfbalelo.elo

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nl.korfbalelo.mijnkorfbal.Scraper
import nl.korfbalelo.mijnkorfbal.StaticPoules
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import kotlin.test.assertFalse
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

    @AfterEach
    fun cleanUpMode() {
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

    @Test
    fun `outdoor post-season fixtures are shown in matching base poules`() {
        val specialDate = LocalDate.of(2026, 6, 13)
        SeasonPredicter.doOutdoor = true

        val pouleType = object : TypeToken<Map<String, Map<String, List<String>>>>() {}.type
        val poulesByTier = Gson().fromJson<Map<String, Map<String, List<String>>>>(
            File("web/public/veld2526vj.json").reader(),
            pouleType,
        )
        poulesByTier.values
            .flatMap { it.entries }
            .forEach { (pouleName, teams) ->
                Scraper.outdoorPoules[pouleName] = teams.associateWith { 0 } to emptyList()
            }

        Scraper.outdoorPoules.values
            .flatMap { it.first.keys }
            .distinct()
            .forEach { teamName ->
                RankingNew.add(
                    Team(teamName, "test", 1500.0).apply {
                        lastDate = specialDate.minusDays(7)
                        rd = 120.0
                        averageScore = 15.0
                    }
                )
            }

        val specialMatch = Match("KZ", "LDODK", -1, -1, specialDate).also {
            it.special = true
        }
        Scraper.outdoorPoules["EK play-off"] = emptyMap<String, Int>() to listOf(specialMatch)
        SeasonPredicter.zeDate = specialDate

        val season = SeasonPredicter.OutdoorSeason()
        val poulesByName = season.predicters.associateBy { it.pouleName }

        assertTrue(poulesByName.getValue("EK-01").matches.any { it.special && it.formatFixture() == specialMatch.formatFixture() })
        assertTrue(poulesByName.getValue("EK-03").matches.any { it.special && it.formatFixture() == specialMatch.formatFixture() })
        assertFalse(poulesByName.getValue("EK-02").matches.any { it.special && it.formatFixture() == specialMatch.formatFixture() })
        assertFalse(poulesByName.getValue("EK-04").matches.any { it.special && it.formatFixture() == specialMatch.formatFixture() })
        assertTrue(SeasonPredicter.shouldWritePouleSnapshot(poulesByName.getValue("EK-01"), specialDate))
        assertTrue(SeasonPredicter.shouldWritePouleSnapshot(poulesByName.getValue("EK-03"), specialDate))
    }
}
