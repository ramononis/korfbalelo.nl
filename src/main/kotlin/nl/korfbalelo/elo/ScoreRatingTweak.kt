package nl.korfbalelo.elo

import java.time.LocalDate
import kotlin.math.absoluteValue
import kotlin.math.sqrt

// AI generated
object ScoreRatingTweak {
    var config: ScoreRatingTweakConfig = ScoreRatingTweakConfig.fromSystemProperties()

    private var regression = ScoreRatingRegression.unavailable()
    private var preparedDate: LocalDate? = null
    private var snapshots = 0
    private var weightedCorrelationSum = 0.0
    private var weightedScorePer100RatingSum = 0.0
    private var weightedRatingPerGoalSum = 0.0
    private var weightedSnapshotTeams = 0
    private var appliedTeamAdjustments = 0
    private var absoluteAdjustmentSum = 0.0
    private var cappedAdjustments = 0

    fun reset() {
        regression = ScoreRatingRegression.unavailable()
        preparedDate = null
        snapshots = 0
        weightedCorrelationSum = 0.0
        weightedScorePer100RatingSum = 0.0
        weightedRatingPerGoalSum = 0.0
        weightedSnapshotTeams = 0
        appliedTeamAdjustments = 0
        absoluteAdjustmentSum = 0.0
        cappedAdjustments = 0
    }

    fun prepare(date: LocalDate, teams: Collection<Team>) {
        preparedDate = date
        regression = ScoreRatingRegression.from(teams, config)
        if (!regression.available) return

        snapshots++
        weightedSnapshotTeams += regression.teams
        weightedCorrelationSum += regression.correlation * regression.teams
        weightedScorePer100RatingSum += regression.scorePerRating * 100.0 * regression.teams
        weightedRatingPerGoalSum += regression.ratingPerGoal * regression.teams
    }

    fun adjust(
        match: Match,
        home: Team,
        away: Team,
        homeUpdate: List<Double>,
        awayUpdate: List<Double>,
    ): Pair<List<Double>, List<Double>> {
        if (config.mode == ScoreRatingTweakMode.OFF || preparedDate != match.date || !regression.available) {
            return homeUpdate to awayUpdate
        }

        val expectedHomeScore = regression.expectedScore(home.rating)
        val expectedAwayScore = regression.expectedScore(away.rating)
        val sharedTotalResidual = (
            match.homeScore + match.awayScore -
                expectedHomeScore -
                expectedAwayScore
            ) / 2.0

        val (homeResidual, awayResidual) = when (config.mode) {
            ScoreRatingTweakMode.OFF -> 0.0 to 0.0
            ScoreRatingTweakMode.MATCH_TOTAL_SCORE -> sharedTotalResidual to sharedTotalResidual
            ScoreRatingTweakMode.MATCH_TEAM_SCORE ->
                match.homeScore - expectedHomeScore to match.awayScore - expectedAwayScore

            ScoreRatingTweakMode.NEW_AVERAGE_SCORE ->
                homeUpdate[3] - regression.expectedScore(homeUpdate[0]) to
                    awayUpdate[3] - regression.expectedScore(awayUpdate[0])

            ScoreRatingTweakMode.HYBRID -> {
                val homeTeamResidual = match.homeScore - expectedHomeScore
                val awayTeamResidual = match.awayScore - expectedAwayScore
                (homeTeamResidual + sharedTotalResidual) / 2.0 to
                    (awayTeamResidual + sharedTotalResidual) / 2.0
            }
        }

        val homeDelta = ratingDelta(homeResidual, home)
        val awayDelta = ratingDelta(awayResidual, away)
        return homeUpdate.withRatingDelta(homeDelta) to awayUpdate.withRatingDelta(awayDelta)
    }

    fun summary(): ScoreRatingTweakSummary {
        val averageCorrelation = weightedCorrelationSum.safeDiv(weightedSnapshotTeams)
        val averageScorePer100Rating = weightedScorePer100RatingSum.safeDiv(weightedSnapshotTeams)
        val averageRatingPerGoal = weightedRatingPerGoalSum.safeDiv(weightedSnapshotTeams)
        val averageAbsoluteAdjustment = absoluteAdjustmentSum.safeDiv(appliedTeamAdjustments)
        return ScoreRatingTweakSummary(
            snapshots = snapshots,
            teamsPerSnapshot = weightedSnapshotTeams.toDouble().safeDiv(snapshots),
            correlation = averageCorrelation,
            scorePer100Rating = averageScorePer100Rating,
            ratingPerGoal = averageRatingPerGoal,
            averageAbsoluteAdjustment = averageAbsoluteAdjustment,
            cappedAdjustments = cappedAdjustments,
        )
    }

    private fun ratingDelta(scoreResidual: Double, team: Team): Double {
        val rdScale = if (config.scaleByRd) {
            (team.rd / (Team.RD_MAX / 2.0)).coerceIn(0.35, 1.75)
        } else {
            1.0
        }
        val raw = scoreResidual * regression.ratingPerGoal * config.learningRate * rdScale
        val clamped = raw.coerceIn(-config.maxAdjustment, config.maxAdjustment)
        if (clamped != raw) cappedAdjustments++
        appliedTeamAdjustments++
        absoluteAdjustmentSum += clamped.absoluteValue
        return clamped
    }

    private fun List<Double>.withRatingDelta(delta: Double): List<Double> =
        if (delta == 0.0) this else toMutableList().also { it[0] += delta }
}

data class ScoreRatingTweakConfig(
    val mode: ScoreRatingTweakMode = ScoreRatingTweakMode.OFF,
    val learningRate: Double = 0.0,
    val maxAdjustment: Double = 10.0,
    val minGames: Int = 8,
    val maxRatingPerGoal: Double = 250.0,
    val scaleByRd: Boolean = false,
) {
    override fun toString(): String =
        if (mode == ScoreRatingTweakMode.OFF) {
            "off"
        } else {
            "${mode.name.lowercase()} lr=$learningRate cap=$maxAdjustment minGames=$minGames rdScale=$scaleByRd"
        }

    companion object {
        private val defaultConfig = ScoreRatingTweakConfig(
            mode = ScoreRatingTweakMode.MATCH_TEAM_SCORE,
            learningRate = 0.001,
            maxAdjustment = 10.0,
            minGames = 4,
            scaleByRd = false,
        )

        fun fromSystemProperties(): ScoreRatingTweakConfig {
            val mode = System.getProperty("elo.model.scoreRating.mode")
                ?.let { ScoreRatingTweakMode.valueOf(it.uppercase()) }
                ?: defaultConfig.mode
            return ScoreRatingTweakConfig(
                mode = mode,
                learningRate = System.getProperty("elo.model.scoreRating.learningRate")?.toDoubleOrNull()
                    ?: defaultConfig.learningRate,
                maxAdjustment = System.getProperty("elo.model.scoreRating.maxAdjustment")?.toDoubleOrNull()
                    ?: defaultConfig.maxAdjustment,
                minGames = System.getProperty("elo.model.scoreRating.minGames")?.toIntOrNull() ?: defaultConfig.minGames,
                maxRatingPerGoal = System.getProperty("elo.model.scoreRating.maxRatingPerGoal")?.toDoubleOrNull()
                    ?: defaultConfig.maxRatingPerGoal,
                scaleByRd = System.getProperty("elo.model.scoreRating.scaleByRd")?.toBooleanStrictOrNull()
                    ?: defaultConfig.scaleByRd,
            )
        }
    }
}

enum class ScoreRatingTweakMode {
    OFF,
    MATCH_TOTAL_SCORE,
    MATCH_TEAM_SCORE,
    NEW_AVERAGE_SCORE,
    HYBRID,
}

data class ScoreRatingTweakSummary(
    val snapshots: Int,
    val teamsPerSnapshot: Double,
    val correlation: Double,
    val scorePer100Rating: Double,
    val ratingPerGoal: Double,
    val averageAbsoluteAdjustment: Double,
    val cappedAdjustments: Int,
) {
    fun compactLine(): String =
        "snapshots=$snapshots, teams=${teamsPerSnapshot.formatMetric()}, " +
            "corr=${correlation.formatMetric()}, score/100rating=${scorePer100Rating.formatMetric()}, " +
            "rating/goal=${ratingPerGoal.formatMetric()}, avgAbsAdj=${averageAbsoluteAdjustment.formatMetric()}, " +
            "capped=$cappedAdjustments"
}

private data class ScoreRatingRegression(
    val available: Boolean,
    val teams: Int,
    val intercept: Double,
    val scorePerRating: Double,
    val ratingPerGoal: Double,
    val correlation: Double,
) {
    fun expectedScore(rating: Double): Double =
        intercept + scorePerRating * rating

    companion object {
        fun unavailable() = ScoreRatingRegression(
            available = false,
            teams = 0,
            intercept = 0.0,
            scorePerRating = 0.0,
            ratingPerGoal = 0.0,
            correlation = Double.NaN,
        )

        fun from(teams: Collection<Team>, config: ScoreRatingTweakConfig): ScoreRatingRegression {
            val stats = RegressionStats()
            teams.asSequence()
                .filter { it.rating != Team.MAGIC_1500 }
                .filter { it.games >= config.minGames }
                .filter { it.averageScore.isFinite() && it.rating.isFinite() }
                .forEach { stats.add(it.rating, it.averageScore) }

            val scorePerRating = stats.slope()
            if (stats.n < 20 || !scorePerRating.isFinite() || scorePerRating <= 0.0) {
                return unavailable()
            }

            val ratingPerGoal = (1.0 / scorePerRating).coerceIn(
                -config.maxRatingPerGoal,
                config.maxRatingPerGoal,
            )
            return ScoreRatingRegression(
                available = true,
                teams = stats.n,
                intercept = stats.intercept(),
                scorePerRating = scorePerRating,
                ratingPerGoal = ratingPerGoal,
                correlation = stats.correlation(),
            )
        }
    }
}

private class RegressionStats {
    var n = 0
        private set
    private var sumX = 0.0
    private var sumY = 0.0
    private var sumXX = 0.0
    private var sumYY = 0.0
    private var sumXY = 0.0

    fun add(x: Double, y: Double) {
        n++
        sumX += x
        sumY += y
        sumXX += x * x
        sumYY += y * y
        sumXY += x * y
    }

    fun slope(): Double {
        val denominator = n * sumXX - sumX * sumX
        return (n * sumXY - sumX * sumY) / denominator
    }

    fun intercept(): Double =
        (sumY - slope() * sumX) / n

    fun correlation(): Double {
        val numerator = n * sumXY - sumX * sumY
        val denominator = sqrt((n * sumXX - sumX * sumX) * (n * sumYY - sumY * sumY))
        return numerator / denominator
    }
}

private fun Double.safeDiv(denominator: Int): Double =
    if (denominator == 0) Double.NaN else this / denominator
