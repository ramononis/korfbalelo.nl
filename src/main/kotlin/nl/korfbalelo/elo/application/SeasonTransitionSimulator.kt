package nl.korfbalelo.elo.application

import nl.korfbalelo.mijnkorfbal.PouleData
import java.util.random.RandomGenerator

// AI generated
interface SeasonTransitionSimulator {
    fun simulate(
        poules: Map<String, PouleData>,
        random: RandomGenerator,
    ): SeasonTransitionResult
}

data class SeasonTransitionResult(
    val tierByTeam: Map<String, String>,
    val eventsByTeam: Map<String, Set<String>> = emptyMap(),
)
