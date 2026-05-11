package nl.korfbalelo.elo

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.absoluteValue
import kotlin.math.pow

// AI generated
object ScoreSeasonality {
    var config: ScoreSeasonalityConfig = ScoreSeasonalityConfig.fromSystemProperties()

    private val monthOffsets = DoubleArray(13)
    private var lastPreparedDate: LocalDate? = null
    private var updates = 0
    private var absoluteResidualSum = 0.0
    private var absoluteOffsetSum = 0.0

    fun reset() {
        monthOffsets.fill(0.0)
        lastPreparedDate = null
        updates = 0
        absoluteResidualSum = 0.0
        absoluteOffsetSum = 0.0
    }

    fun prepare(date: LocalDate) {
        val previousDate = lastPreparedDate
        if (
            config.mode != ScoreSeasonalityMode.OFF &&
            previousDate != null &&
            config.halfLifeDays > 0.0
        ) {
            val days = ChronoUnit.DAYS.between(previousDate, date).coerceAtLeast(0)
            if (days > 0) {
                val decay = 0.5.pow(days / config.halfLifeDays)
                for (month in 1..12) {
                    monthOffsets[month] *= decay
                }
            }
        }
        lastPreparedDate = date
    }

    fun adjustedAverageScore(averageScore: Double, date: LocalDate?): Double =
        (averageScore + offset(date)).coerceAtLeast(RatingModel.config.minAverageScore)

    fun learn(match: Match, home: Team, away: Team, diffDistro: ND) {
        if (config.mode == ScoreSeasonalityMode.OFF || match.homeScore < 0 || match.awayScore < 0) return

        val homeBase = adjustedAverageScore(home.averageScore, match.date)
        val awayBase = adjustedAverageScore(away.averageScore, match.date)
        val expectedHomeScore = (homeBase + awayBase + diffDistro.first) / 2.0
        val expectedAwayScore = (homeBase + awayBase - diffDistro.first) / 2.0
        val residualPerTeam = (
            match.homeScore + match.awayScore -
                expectedHomeScore -
                expectedAwayScore
            ) / 2.0

        val month = match.date.monthValue
        monthOffsets[month] = (monthOffsets[month] + residualPerTeam * config.learningRate)
            .coerceIn(-config.maxAdjustment, config.maxAdjustment)
        updates++
        absoluteResidualSum += residualPerTeam.absoluteValue
        absoluteOffsetSum += monthOffsets[month].absoluteValue
    }

    fun summary(): ScoreSeasonalitySummary =
        ScoreSeasonalitySummary(
            updates = updates,
            averageAbsoluteResidual = absoluteResidualSum.safeDiv(updates),
            averageAbsoluteOffset = absoluteOffsetSum.safeDiv(updates),
            monthOffsets = monthOffsets.drop(1),
        )

    private fun offset(date: LocalDate?): Double =
        if (config.mode == ScoreSeasonalityMode.OFF || date == null) {
            0.0
        } else {
            monthOffsets[date.monthValue]
        }
}

data class ScoreSeasonalityConfig(
    val mode: ScoreSeasonalityMode = ScoreSeasonalityMode.MONTHLY_OFFSET,
    val learningRate: Double = 0.0025,
    val maxAdjustment: Double = 6.0,
    val halfLifeDays: Double = 1825.0,
) {
    override fun toString(): String =
        if (mode == ScoreSeasonalityMode.OFF) {
            "off"
        } else {
            "${mode.name.lowercase()} lr=$learningRate cap=$maxAdjustment halfLife=$halfLifeDays"
        }

    companion object {
        val standard = ScoreSeasonalityConfig()
        val off = ScoreSeasonalityConfig(
            mode = ScoreSeasonalityMode.OFF,
            learningRate = 0.0,
            maxAdjustment = 6.0,
            halfLifeDays = 0.0,
        )

        fun fromSystemProperties(): ScoreSeasonalityConfig =
            ScoreSeasonalityConfig(
                mode = System.getProperty("elo.model.scoreSeasonality.mode")
                    ?.let { ScoreSeasonalityMode.valueOf(it.uppercase()) }
                    ?: standard.mode,
                learningRate = System.getProperty("elo.model.scoreSeasonality.learningRate")?.toDoubleOrNull()
                    ?: standard.learningRate,
                maxAdjustment = System.getProperty("elo.model.scoreSeasonality.maxAdjustment")?.toDoubleOrNull()
                    ?: standard.maxAdjustment,
                halfLifeDays = System.getProperty("elo.model.scoreSeasonality.halfLifeDays")?.toDoubleOrNull()
                    ?: standard.halfLifeDays,
            )
    }
}

enum class ScoreSeasonalityMode {
    OFF,
    MONTHLY_OFFSET,
}

data class ScoreSeasonalitySummary(
    val updates: Int,
    val averageAbsoluteResidual: Double,
    val averageAbsoluteOffset: Double,
    val monthOffsets: List<Double>,
) {
    fun compactLine(): String =
        "seasonUpdates=$updates, avgResidual=${averageAbsoluteResidual.formatMetric()}, " +
            "avgOffset=${averageAbsoluteOffset.formatMetric()}, months=" +
            monthOffsets.joinToString(prefix = "[", postfix = "]") { it.formatMetric() }
}

private fun Double.safeDiv(denominator: Int): Double =
    if (denominator == 0) Double.NaN else this / denominator
