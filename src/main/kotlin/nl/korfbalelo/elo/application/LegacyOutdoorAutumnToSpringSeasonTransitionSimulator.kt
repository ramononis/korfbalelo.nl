package nl.korfbalelo.elo.application

import nl.korfbalelo.elo.PoulePredicter
import nl.korfbalelo.elo.Team
import nl.korfbalelo.mijnkorfbal.PouleData
import java.util.random.RandomGenerator

// AI generated
// Legacy autumn->spring veld placement logic kept behind a small interface so it can be replaced cleanly.
class LegacyOutdoorAutumnToSpringSeasonTransitionSimulator : SeasonTransitionSimulator {
    override fun simulate(
        poules: Map<String, PouleData>,
        random: RandomGenerator,
    ): SeasonTransitionResult {
        val predicters = poules.entries
            .sortedBy { it.key }
            .associate { (pouleName, pouleData) ->
                pouleName to PoulePredicter(pouleName, pouleData.first, pouleData.second, null)
            }

        with(random) {
            predicters.values.forEach { it.simulate() }
        }

        fun filtered(regex: Regex, size: Int) = predicters
            .filterKeys { regex.matchEntire(it) != null }
            .values
            .sortedBy(PoulePredicter::pouleName)
            .also { require(it.size == size) { "Unexpected poule count for $regex: ${it.size}" } }

        fun standing(poule: PoulePredicter) = poule.executor.result

        val tierByTeam = mutableMapOf<String, String>()
        fun assign(team: Team, tier: String) {
            check(tierByTeam.put(team.name, tier) == null) { "Team ${team.name} already assigned" }
        }

        filtered(Regex("EK-\\d*"), 8).forEach { poule ->
            val result = standing(poule).map { it.first }
            result.take(3).forEach { assign(it, "ek") }
            assign(result[3], "hk")
        }

        filtered(Regex("HK-.*"), 8).forEach { poule ->
            val result = standing(poule).map { it.first }
            assign(result[0], "ek")
            assign(result[1], "hk")
            assign(result[2], "hk")
            assign(result[3], "ok")
        }

        val oks = filtered(Regex("OK-.*"), 8)
        val okThirds = oks
            .map { standing(it)[2] to (standing(it).size - 1) }
            .sortedWith(::compareSimilarTeams)
        oks.forEach { poule ->
            val result = standing(poule).map { it.first }
            assign(result[0], "hk")
            assign(result[1], "ok")
            assign(result[3], "1k")
        }
        okThirds.take(4).forEach { (teamAndStats) -> assign(teamAndStats.first, "ok") }
        okThirds.drop(4).forEach { (teamAndStats) -> assign(teamAndStats.first, "1k") }

        filtered(Regex("1-.*"), 12).forEach { poule ->
            val result = standing(poule).map { it.first }
            assign(result[0], "ok")
            assign(result[1], "1k")
            assign(result[2], "1k")
            assign(result[3], "2k")
        }

        filtered(Regex("2.*"), 12).forEach { poule ->
            val result = standing(poule).map { it.first }
            assign(result[0], "1k")
            assign(result[1], "2k")
            assign(result[2], "2k")
            assign(result[3], "3k")
        }

        filtered(Regex("3.*"), 12).forEach { poule ->
            val result = standing(poule).map { it.first }
            assign(result[0], "2k")
            assign(result[1], "3k")
            assign(result[2], "3k")
            assign(result[3], "4k")
        }

        val fourths = filtered(Regex("4.*"), 10)
        val bestSeconds = fourths
            .map { standing(it)[1] to (standing(it).size - 1) }
            .sortedWith(::compareSimilarTeams)
            .take(2)
            .map { it.first.first.name }
            .toSet()
        fourths.forEach { poule ->
            val result = standing(poule).map { it.first }
            assign(result[0], "3k")
            if (result[1].name in bestSeconds) {
                assign(result[1], "3k")
                result.drop(2).forEach { assign(it, "4k") }
            } else {
                result.drop(1).forEach { assign(it, "4k") }
            }
        }

        return SeasonTransitionResult(tierByTeam = tierByTeam)
    }

    private fun compareSimilarTeams(
        a: Pair<Pair<Team, Triple<Int, Int, Int>>, Int>,
        b: Pair<Pair<Team, Triple<Int, Int, Int>>, Int>,
    ): Int {
        val (teamAndStatsA, factorA) = a
        val (teamAndStatsB, factorB) = b
        val statsA = teamAndStatsA.second
        val statsB = teamAndStatsB.second
        return (statsB.first * factorA - statsA.first * factorB).takeIf { it != 0 }
            ?: (statsB.second * factorA - statsA.second * factorB).takeIf { it != 0 }
            ?: (statsB.third * factorA - statsA.third * factorB).takeIf { it != 0 }
            ?: teamAndStatsA.first.name.compareTo(teamAndStatsB.first.name)
    }
}
