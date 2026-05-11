package nl.korfbalelo.elo

import nl.korfbalelo.elo.application.DeclarativeSeasonTransitionSimulator
import nl.korfbalelo.elo.application.SeasonTransitionOverrides
import nl.korfbalelo.elo.application.SeasonTransitionResult
import nl.korfbalelo.elo.application.SeasonTransitionDefinition
import nl.korfbalelo.elo.application.TransitionGroup
import nl.korfbalelo.elo.application.TransitionOverrideAction
import nl.korfbalelo.elo.application.TransitionRule
import nl.korfbalelo.elo.application.VacancyChainDefinition
import nl.korfbalelo.elo.application.VacancyChainStep
import nl.korfbalelo.mijnkorfbal.PouleData
import nl.korfbalelo.mijnkorfbal.Scraper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.SplittableRandom
import kotlin.test.assertEquals

class DeclarativeSeasonTransitionOverrideTest {
    @BeforeEach
    fun resetState() {
        ApplicationNew.log = false
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
    }

    @Test
    fun `baseline count override can trigger vacancy chain without an explicit withdrawal`() {
        val poules = mapOf(
            "A-01" to orderedPoule(
                "A1", "A2", "A3", "A4",
                listOf(
                    score("A1", "A2", 10, 5),
                    score("A1", "A3", 10, 4),
                    score("A1", "A4", 10, 3),
                    score("A2", "A3", 9, 5),
                    score("A2", "A4", 9, 4),
                    score("A3", "A4", 8, 4),
                ),
            ),
            "B-01" to orderedPoule(
                "B1", "B2", "B3", "B4",
                listOf(
                    score("B1", "B2", 10, 5),
                    score("B1", "B3", 10, 4),
                    score("B1", "B4", 10, 3),
                    score("B2", "B3", 9, 5),
                    score("B2", "B4", 9, 4),
                    score("B3", "B4", 8, 4),
                ),
            ),
        )

        poules.values.flatMap { it.first.keys }.forEach { teamName ->
            RankingNew.add(
                Team(teamName, "test", 1500.0).apply {
                    lastDate = LocalDate.of(2026, 1, 1)
                }
            )
        }

        val simulator = DeclarativeSeasonTransitionSimulator(
            definition = SeasonTransitionDefinition(
                id = "baseline-override-test",
                tierOrder = listOf("a", "b"),
                groups = listOf(
                    TransitionGroup("a", "^A-.*$", 1),
                    TransitionGroup("b", "^B-.*$", 1),
                ),
                baselineCountOverrides = mapOf(
                    "a" to 2,
                ),
                vacancyChains = listOf(
                    VacancyChainDefinition(
                        targetTier = "a",
                        steps = listOf(
                            VacancyChainStep("a", 2),
                            VacancyChainStep("b", 1),
                        ),
                    ),
                ),
                rules = listOf(
                    TransitionRule("a-winner", "direct", groupId = "a", positions = listOf(1), tier = "a"),
                    TransitionRule("a-rest", "direct", groupId = "a", positions = listOf(2, 3, 4), tier = "b"),
                    TransitionRule("b-all", "direct", groupId = "b", positions = listOf(1, 2, 3, 4), tier = "b"),
                ),
            ),
        )

        val result = simulator.simulate(poules, SplittableRandom(0))

        assertEquals("a", result.tierByTeam["A2"])
        assertEquals("b", result.tierByTeam["B1"])
    }

    @Test
    fun `vacancy chain keeps best same-class team before promoting from lower class`() {
        val poules = mapOf(
            "A-01" to orderedPoule(
                "A1", "A2", "A3", "A4",
                listOf(
                    score("A1", "A2", 10, 5),
                    score("A1", "A3", 10, 4),
                    score("A1", "A4", 10, 3),
                    score("A2", "A3", 9, 5),
                    score("A2", "A4", 9, 4),
                    score("A3", "A4", 8, 4),
                ),
            ),
            "A-02" to orderedPoule(
                "C1", "C2", "C3", "C4",
                listOf(
                    score("C1", "C2", 10, 5),
                    score("C1", "C3", 10, 3),
                    score("C1", "C4", 10, 2),
                    score("C2", "C3", 9, 4),
                    score("C2", "C4", 9, 3),
                    score("C3", "C4", 6, 5),
                ),
            ),
            "B-01" to orderedPoule(
                "B1", "B2", "B3", "B4",
                listOf(
                    score("B1", "B2", 10, 5),
                    score("B1", "B3", 10, 4),
                    score("B1", "B4", 10, 3),
                    score("B2", "B3", 9, 5),
                    score("B2", "B4", 9, 4),
                    score("B3", "B4", 8, 4),
                ),
            ),
            "B-02" to orderedPoule(
                "D1", "D2", "D3", "D4",
                listOf(
                    score("D1", "D2", 10, 5),
                    score("D1", "D3", 10, 4),
                    score("D1", "D4", 10, 3),
                    score("D2", "D3", 9, 5),
                    score("D2", "D4", 9, 4),
                    score("D3", "D4", 8, 4),
                ),
            ),
        )

        poules.values.flatMap { it.first.keys }.forEach { teamName ->
            RankingNew.add(
                Team(teamName, "test", 1500.0).apply {
                    lastDate = LocalDate.of(2026, 1, 1)
                }
            )
        }

        val simulator = DeclarativeSeasonTransitionSimulator(
            definition = SeasonTransitionDefinition(
                id = "vacancy-test",
                tierOrder = listOf("a", "b"),
                groups = listOf(
                    TransitionGroup("a", "^A-.*$", 2),
                    TransitionGroup("b", "^B-.*$", 2),
                ),
                vacancyChains = listOf(
                    VacancyChainDefinition(
                        targetTier = "a",
                        steps = listOf(
                            VacancyChainStep("a", 3),
                            VacancyChainStep("b", 2),
                        ),
                    ),
                ),
                rules = listOf(
                    TransitionRule("a-top2", "direct", groupId = "a", positions = listOf(1, 2), tier = "a"),
                    TransitionRule("a-rest", "direct", groupId = "a", positions = listOf(3, 4), tier = "b"),
                    TransitionRule("b-all", "direct", groupId = "b", positions = listOf(1, 2, 3, 4), tier = "b"),
                ),
            ),
            overrides = SeasonTransitionOverrides(
                actions = listOf(
                    TransitionOverrideAction(type = "withdraw", team = "A1"),
                ),
            ),
        )

        val result: SeasonTransitionResult = simulator.simulate(poules, SplittableRandom(0))

        assertEquals("a", result.tierByTeam["A3"])
        assertEquals("b", result.tierByTeam["B2"])
    }

    @Test
    fun `withdrawn promoted poule winner passes promotion right to runner-up`() {
        val poules = mapOf(
            "A-01" to orderedPoule(
                "A1", "A2", "A3", "A4",
                listOf(
                    score("A1", "A2", 10, 5),
                    score("A1", "A3", 10, 4),
                    score("A1", "A4", 10, 3),
                    score("A2", "A3", 9, 5),
                    score("A2", "A4", 9, 4),
                    score("A3", "A4", 8, 4),
                ),
            ),
        )

        poules.values.flatMap { it.first.keys }.forEach { teamName ->
            RankingNew.add(
                Team(teamName, "test", 1500.0).apply {
                    lastDate = LocalDate.of(2026, 1, 1)
                }
            )
        }

        val simulator = DeclarativeSeasonTransitionSimulator(
            definition = SeasonTransitionDefinition(
                id = "winner-withdraw-test",
                tierOrder = listOf("a", "b"),
                groups = listOf(
                    TransitionGroup("b", "^A-.*$", 1),
                ),
                rules = listOf(
                    TransitionRule("promote-winner", "direct", groupId = "b", positions = listOf(1), tier = "a"),
                    TransitionRule("rest-stay", "direct", groupId = "b", positions = listOf(2, 3, 4), tier = "b"),
                ),
            ),
            overrides = SeasonTransitionOverrides(
                actions = listOf(
                    TransitionOverrideAction(type = "withdraw", team = "A1"),
                ),
            ),
        )

        val result = simulator.simulate(poules, SplittableRandom(0))

        assertEquals("a", result.tierByTeam["A2"])
    }

    @Test
    fun `declining promoted poule winner passes promotion right to runner-up`() {
        val poules = mapOf(
            "A-01" to orderedPoule(
                "A1", "A2", "A3", "A4",
                listOf(
                    score("A1", "A2", 10, 5),
                    score("A1", "A3", 10, 4),
                    score("A1", "A4", 10, 3),
                    score("A2", "A3", 9, 5),
                    score("A2", "A4", 9, 4),
                    score("A3", "A4", 8, 4),
                ),
            ),
        )

        poules.values.flatMap { it.first.keys }.forEach { teamName ->
            RankingNew.add(
                Team(teamName, "test", 1500.0).apply {
                    lastDate = LocalDate.of(2026, 1, 1)
                }
            )
        }

        val simulator = DeclarativeSeasonTransitionSimulator(
            definition = SeasonTransitionDefinition(
                id = "winner-decline-test",
                tierOrder = listOf("a", "b"),
                groups = listOf(
                    TransitionGroup("b", "^A-.*$", 1),
                ),
                rules = listOf(
                    TransitionRule("promote-winner", "direct", groupId = "b", positions = listOf(1), tier = "a"),
                    TransitionRule("rest-stay", "direct", groupId = "b", positions = listOf(2, 3, 4), tier = "b"),
                ),
            ),
            overrides = SeasonTransitionOverrides(
                actions = listOf(
                    TransitionOverrideAction(type = "declinePromotion", team = "A1", targetTier = "b"),
                ),
            ),
        )

        val result = simulator.simulate(poules, SplittableRandom(0))

        assertEquals("a", result.tierByTeam["A2"])
        assertEquals("b", result.tierByTeam["A1"])
    }

    private fun orderedPoule(
        team1: String,
        team2: String,
        team3: String,
        team4: String,
        matches: List<Match>,
    ): PouleData = mapOf(team1 to 0, team2 to 0, team3 to 0, team4 to 0) to matches

    private fun score(home: String, away: String, homeScore: Int, awayScore: Int): Match =
        Match(home, away, homeScore, awayScore, LocalDate.of(2026, 1, 1))
}
