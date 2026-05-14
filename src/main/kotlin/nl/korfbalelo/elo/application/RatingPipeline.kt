package nl.korfbalelo.elo.application

import nl.korfbalelo.elo.AccuracyTracker
import nl.korfbalelo.elo.DiscontinuedTeams
import nl.korfbalelo.elo.PredictionBenchmark
import nl.korfbalelo.elo.RankingEvent
import nl.korfbalelo.elo.RankingNew
import nl.korfbalelo.elo.ScoreRatingTweak
import nl.korfbalelo.elo.ScoreSeasonality
import nl.korfbalelo.elo.SpawnEvent
import nl.korfbalelo.elo.Team
import nl.korfbalelo.elo.domain.RatingRunResult
import nl.korfbalelo.elo.domain.RatingRunWindow
import nl.korfbalelo.elo.domain.RatingSnapshotRow
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation
import java.time.LocalDate
import kotlin.math.absoluteValue

// AI generated
class RatingPipeline(
    private val splitDate: LocalDate = AccuracyTracker.splitDate,
    private val trackStartDate: LocalDate = AccuracyTracker.trackStartDate,
) {
    fun run(
        events: Set<RankingEvent>,
        window: RatingRunWindow,
        activeTeams: Set<String>,
        logEnabled: Boolean,
        rebalanceRatings: Boolean = logEnabled,
        onDate: (LocalDate) -> Unit,
    ): RatingRunResult {
        Team.reset()
        PredictionBenchmark.reset()
        ScoreRatingTweak.reset()
        ScoreSeasonality.reset()
        RankingNew.ranking.clear()
        RankingNew.aliases.clear()
        RankingNew.graph.clear()
        DiscontinuedTeams.clear()

        val old = events.filter { it.date.isBefore(splitDate) }
        val new = events.filter { !it.date.isBefore(splitDate) }

        var started = false
        val hasDateWindow = !window.from.isAfter(window.to)

        fun eachDate(date: LocalDate, dateEvents: List<RankingEvent>) {
            if (logEnabled) {
                if (hasDateWindow && !started && !date.isBefore(window.from)) {
                    onDate(window.from)
                    started = true
                }
                if (date.isAfter(trackStartDate)) {
                    Team.trackCurrent = true
                }
            } else if (!rebalanceRatings && dateEvents.any { it is SpawnEvent }) {
                RankingNew.rebalance()
            }

            ScoreSeasonality.prepare(date)
            ScoreRatingTweak.prepare(date, RankingNew.ranking.values)
            dateEvents.forEach(RankingEvent::run)

            if (rebalanceRatings) {
                RankingNew.rebalance()
            }
            if (logEnabled) {
                val average = RankingNew.ranking.values.map(Team::rating).average()
                if ((average - 1500.0).absoluteValue > 0.1) {
                    error("$average$date")
                }
                if (date in window.from..window.to) {
                    onDate(date)
                }
                RankingNew.graph.addRanking(
                    date,
                    "sd",
                    StandardDeviation().evaluate(RankingNew.ranking.values.map(Team::rating).toDoubleArray()).toInt(),
                )
            }
        }

        old.groupBy { it.date }.entries.sortedBy { it.key }.forEach { (date, dateEvents) ->
            eachDate(date, dateEvents)
        }

        val preSplitRanking = if (logEnabled) {
            RankingNew.ranking.values
                .sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.name })
                .map { RatingSnapshotRow(it.name, it.rating, it.rd, it.lastDate) }
        } else {
            emptyList()
        }

        new.groupBy { it.date }.entries.sortedBy { it.key }.forEach { (date, dateEvents) ->
            eachDate(date, dateEvents)
        }

        val oneYearAgo = LocalDate.now().minusYears(1)
        activeTeams.forEach { teamName ->
            val team = RankingNew.ranking.getValue(teamName)
            val lastDate = team.lastDate
            if (lastDate == null || lastDate.isBefore(oneYearAgo)) {
                team.lastDate = LocalDate.now()
            }
        }

        return RatingRunResult(preSplitRanking = preSplitRanking)
    }
}
