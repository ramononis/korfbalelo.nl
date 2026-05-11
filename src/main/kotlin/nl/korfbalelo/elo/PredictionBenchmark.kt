package nl.korfbalelo.elo

import kotlin.math.absoluteValue
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

private const val LOG_LOSS_FLOOR = 1.0e-15

// AI generated
object PredictionBenchmark {

    var range = AccuracyTracker.accuracyRange
    var ratingDeltaMagnitude = 0.0
    var minRatingDeltaMagnitude = Double.MAX_VALUE
    var accuracy = PredictionWindowStats()
        private set
    var current = PredictionWindowStats()
        private set

    fun reset() {
        ratingDeltaMagnitude = 0.0
        accuracy = PredictionWindowStats()
        current = PredictionWindowStats()
    }

    fun finishRun() {
        minRatingDeltaMagnitude = minOf(minRatingDeltaMagnitude, ratingDeltaMagnitude)
    }

    fun recordRatingDelta(delta: Double) {
        ratingDeltaMagnitude += delta
    }

    fun recordStartRatingClampPenalty(delta: Double) {
        ratingDeltaMagnitude += 1.0e-10 * delta
    }

    fun record(match: Match, home: Team, away: Team, diffDistro: ND) {
        if (match.homeScore < 0 || match.awayScore < 0) return
        if (match.date in range.first..range.second) {
            accuracy.record(match, home, away, diffDistro)
        }
        if (Team.trackCurrent) {
            current.record(match, home, away, diffDistro)
        }
    }
}

class PredictionWindowStats {
    private val ratingScoreCorrelation = OnlineCorrelation()
    private val ratingGoalsCorrelation = OnlineCorrelation()
    private val averageScoreGoalsCorrelation = OnlineCorrelation()

    var matches = 0
        private set
    var decisiveMatches = 0
        private set
    var weightedMargin = 0
        private set
    var weightedCorrectMargin = 0
        private set
    var signCorrect = 0
        private set
    var wdlCorrect = 0
        private set
    var exactScoreCorrect = 0
        private set
    var brierSum = 0.0
        private set
    var logLossSum = 0.0
        private set
    var marginAbsoluteError = 0.0
        private set
    var totalScoreAbsoluteError = 0.0
        private set
    var teamScoreAbsoluteError = 0.0
        private set

    fun record(match: Match, home: Team, away: Team, diffDistro: ND) {
        val actualDiff = match.homeScore - match.awayScore
        val actualTotal = match.homeScore + match.awayScore
        val expectedMargin = diffDistro.first
        val averageTotalScore = home.averageScore + away.averageScore
        val expectedHomeScore = (averageTotalScore + expectedMargin) / 2.0
        val expectedAwayScore = (averageTotalScore - expectedMargin) / 2.0
        val expectedHomeScoreRounded = expectedHomeScore.roundToInt()
        val expectedAwayScoreRounded = expectedAwayScore.roundToInt()

        val pHome = 1.0 - diffDistro.cdf(0.5)
        val pDraw = diffDistro.cdf(0.5) - diffDistro.cdf(-0.5)
        val pAway = diffDistro.cdf(-0.5)

        matches++
        weightedMargin += actualDiff.absoluteValue
        if (actualDiff != 0) {
            decisiveMatches++
            if (actualDiff.sign == expectedMargin.sign.toInt()) {
                signCorrect++
                weightedCorrectMargin += actualDiff.absoluteValue
            }
        }

        val actualHome = if (actualDiff > 0) 1.0 else 0.0
        val actualDraw = if (actualDiff == 0) 1.0 else 0.0
        val actualAway = if (actualDiff < 0) 1.0 else 0.0
        if (predictedOutcome(pHome, pDraw, pAway) == actualOutcome(actualDiff)) {
            wdlCorrect++
        }
        if (expectedHomeScoreRounded == match.homeScore && expectedAwayScoreRounded == match.awayScore) {
            exactScoreCorrect++
        }

        brierSum += (pHome - actualHome).pow(2) + (pDraw - actualDraw).pow(2) + (pAway - actualAway).pow(2)
        logLossSum += -ln(
            when {
                actualDiff > 0 -> pHome
                actualDiff < 0 -> pAway
                else -> pDraw
            }.coerceAtLeast(LOG_LOSS_FLOOR)
        )
        marginAbsoluteError += (expectedMargin - actualDiff).absoluteValue
        totalScoreAbsoluteError += (averageTotalScore - actualTotal).absoluteValue
        teamScoreAbsoluteError += (expectedHomeScore - match.homeScore).absoluteValue
        teamScoreAbsoluteError += (expectedAwayScore - match.awayScore).absoluteValue

        ratingScoreCorrelation.add(home.rating, home.averageScore)
        ratingScoreCorrelation.add(away.rating, away.averageScore)
        ratingGoalsCorrelation.add(home.rating, match.homeScore.toDouble())
        ratingGoalsCorrelation.add(away.rating, match.awayScore.toDouble())
        averageScoreGoalsCorrelation.add(home.averageScore, match.homeScore.toDouble())
        averageScoreGoalsCorrelation.add(away.averageScore, match.awayScore.toDouble())
    }

    fun summary() = PredictionBenchmarkSummary(
        matches = matches,
        weightedMarginAccuracy = weightedCorrectMargin.safeDiv(weightedMargin),
        decisiveAccuracy = signCorrect.safeDiv(decisiveMatches),
        wdlAccuracy = wdlCorrect.safeDiv(matches),
        brier = brierSum.safeDiv(matches),
        logLoss = logLossSum.safeDiv(matches),
        marginMae = marginAbsoluteError.safeDiv(matches),
        totalScoreMae = totalScoreAbsoluteError.safeDiv(matches),
        teamScoreMae = teamScoreAbsoluteError.safeDiv(matches) / 2.0,
        exactScoreAccuracy = exactScoreCorrect.safeDiv(matches),
        ratingAverageScoreCorrelation = ratingScoreCorrelation.pearson(),
        ratingGoalsCorrelation = ratingGoalsCorrelation.pearson(),
        averageScoreGoalsCorrelation = averageScoreGoalsCorrelation.pearson(),
    )

    private fun actualOutcome(diff: Int) = when {
        diff > 0 -> 1
        diff < 0 -> -1
        else -> 0
    }

    private fun predictedOutcome(pHome: Double, pDraw: Double, pAway: Double) = when (maxOf(pHome, pDraw, pAway)) {
        pHome -> 1
        pAway -> -1
        else -> 0
    }
}

data class PredictionBenchmarkSummary(
    val matches: Int,
    val weightedMarginAccuracy: Double,
    val decisiveAccuracy: Double,
    val wdlAccuracy: Double,
    val brier: Double,
    val logLoss: Double,
    val marginMae: Double,
    val totalScoreMae: Double,
    val teamScoreMae: Double,
    val exactScoreAccuracy: Double,
    val ratingAverageScoreCorrelation: Double,
    val ratingGoalsCorrelation: Double,
    val averageScoreGoalsCorrelation: Double,
) {
    fun compactLine(): String =
        "matches=$matches, weighted=${weightedMarginAccuracy.formatPct()}, " +
            "wdl=${wdlAccuracy.formatPct()}, brier=${brier.formatMetric()}, " +
            "logLoss=${logLoss.formatMetric()}, marginMae=${marginMae.formatMetric()}, " +
            "teamScoreMae=${teamScoreMae.formatMetric()}, " +
            "corr(rating,avgScore)=${ratingAverageScoreCorrelation.formatMetric()}"
}

private class OnlineCorrelation {
    private var n = 0
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

    fun pearson(): Double {
        if (n < 2) return Double.NaN
        val numerator = n * sumXY - sumX * sumY
        val denominator = sqrt((n * sumXX - sumX * sumX) * (n * sumYY - sumY * sumY))
        return numerator / denominator
    }
}

fun Double.formatPct(): String = "%.5f%%".format(this * 100.0)

fun Double.formatMetric(): String = "%.6f".format(this)

private fun Int.safeDiv(denominator: Int): Double =
    if (denominator == 0) Double.NaN else toDouble() / denominator

private fun Double.safeDiv(denominator: Int): Double =
    if (denominator == 0) Double.NaN else this / denominator
