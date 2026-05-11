package nl.korfbalelo.elo

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.random.RandomGenerator
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

data class RatingUpdate(
    val rating: Double,
    val rd: Double,
    val rv: Double,
    val averageScore: Double,
) {
    fun withRatingDelta(delta: Double): RatingUpdate =
        if (delta == 0.0) this else copy(rating = rating + delta)
}

class Team {

    @JvmField
    var name: String
    var created: LocalDate? = null
    var lastDate: LocalDate? = null
    var firstMatchDate: LocalDate? = null
    var lastMatchDate: LocalDate? = null
    var games: Int = 0
    var place: String = ""
    var topRating: Double = Double.NaN
    var rating: Double = Double.NaN
        set(value) {
            if (value.isNaN()) {
                error("team rating for $name became NaN")
            }
            field = value
            if (value != MAGIC_1500) {
                topRating = when {
                    topRating.isNaN() -> value
                    else -> max(topRating, value)
                }
            }
        }

    var rd: Double = RD_MAX / 2.0
    var rv: Double = Glicko2Calculator.DEFAULT_VOLATILITY
    var averageScore = 5.0
    var currentDiff = 0.0
    var origins = 1
    var startOffset = 0.0
    var graphSeriesLabel: String? = null

    constructor(name: String, place: String, rating: Double) {
        this.name = name
        this.rating = rating
        this.place = place
    }

    constructor(name: String, place: String, games: Int, date: LocalDate) {
        this.name = name
        this.created = date
        this.games = games
        this.place = place
    }

    val opponents = mutableMapOf<String, Pair<Int, Double>>()

    context(_: RandomGenerator)
    fun sampleRating(date: LocalDate) {
        setNewRD(date)
        rating = ND(rating, rd).sample()
        rd = 0.0
    }

    fun setNewRD(date: LocalDate) {
        val periods = ChronoUnit.DAYS.between(lastDate ?: date, date) / RD_PERIOD_DAYS
        rd = Glicko2Calculator.ageRatingDeviation(
            ratingDeviation = rd,
            volatility = rv,
            periods = periods,
            maxRatingDeviation = RD_MAX,
        )
        lastDate = date
    }

    fun newRating(
        opponent: Team,
        _for: Int,
        against: Int,
        atHome: Boolean,
        match: Match,
        diffDistro: ND
    ): RatingUpdate {
        val diff = _for - against
        val ownAverageScore = ScoreSeasonality.adjustedAverageScore(averageScore, match.date)
        val opponentAverageScore = ScoreSeasonality.adjustedAverageScore(opponent.averageScore, match.date)
        val sumAve = (ownAverageScore + opponentAverageScore) / 2
        val matchScore = 1.0 - diffDistro.cdf(diffDistro.first - diff.toDouble())
        val expScore = sumAve + diffDistro.first * 0.5
        val homeAdvantageForTeam = if (atHome) H else -H
        val glickoUpdate = Glicko2Calculator.update(
            player = Glicko2Rating(
                rating = rating,
                ratingDeviation = rd,
                volatility = rv,
            ),
            game = Glicko2Game(
                opponent = Glicko2Rating(
                    rating = opponent.rating,
                    ratingDeviation = opponent.rd,
                    volatility = opponent.rv,
                ),
                score = matchScore,
                opponentRatingPenalty = homeAdvantageForTeam,
            ),
            maxRatingDeviation = RD_MAX,
        )
        PredictionBenchmark.recordRatingDelta(glickoUpdate.normalizedRatingDelta.absoluteValue)
        val rPrime = glickoUpdate.rating.rating
        val rdPrime = glickoUpdate.rating.ratingDeviation

        if (trackCurrent) currentDiff += rPrime - rating
        val scoreLearningWeight = glickoUpdate.ratingPeriodVariance * glickoUpdate.opponentImpact
        val newAverageScore = averageScore + (_for - expScore) / SCORE_SPEED_INV * scoreLearningWeight

        if (ApplicationNew.log) {
            if (atHome) {
                match.pHome = 1.0 - diffDistro.cdf(0.5)
                match.pDraw = diffDistro.cdf(0.5) - diffDistro.cdf(-0.5)
                match.pAway = diffDistro.cdf(-0.5)
                match.guessHome = expScore.roundToInt()
                match.guessAway = (expScore - diffDistro.first).roundToInt()
                if (match.guessHome == match.guessAway) {
                    val adjustedOpponentRating = opponent.rating - homeAdvantageForTeam
                    match.guessHome += (rating - adjustedOpponentRating).sign.toInt()
                }
                match.homeDiff = rPrime - rating
                match.homeRating = rating
                match.awayRating = opponent.rating
                match.homeRd = rd.roundToInt()
                match.awayRd = opponent.rd.roundToInt()
                match.actualHome = this
                match.actualAway = opponent
            } else {
                match.awayDiff = rPrime - rating
            }
        }

        if (atHome) {
            H += glickoUpdate.estimatedImprovement * H_SPEED
        }

        games++

        check(rPrime != MAGIC_1500)
        return RatingUpdate(
            rating = rPrime,
            rd = rdPrime,
            rv = glickoUpdate.rating.volatility,
            averageScore = newAverageScore,
        )
    }

    fun setNewRating(update: RatingUpdate) {
        rating = update.rating
        rd = update.rd
        rv = update.rv
        averageScore = max(RatingModel.config.minAverageScore, update.averageScore)
    }

    override fun toString(): String {
        return "Team{" +
            "name='" + name + '\'' +
            ", rating=" + rating +
            ", date=" + lastDate +
            ", place=" + place +
            ", age=" + games +
            ", origins=" + origins +
            ", RD=" + rd +
            ", σ=" + rv +
            '}'
    }

    val fullName: String
        get() {
            return "${name.replace(Regex(" \\(.*\\)"), "")} ($place)"
        }

    val graphName: String
        get() = graphSeriesLabel ?: fullName

    companion object {
        const val MAGIC_1500 = 1500.0000000001
        val allTeams = mutableSetOf<Team>()
        var trackCurrent = false
        val RD_MAX: Double
            get() = RatingModel.config.rdMax
        val RD_PERIOD_DAYS: Double
            get() = RatingModel.config.rdPeriodDays
        val H_SPEED: Double
            get() = RatingModel.config.homeAdvantageSpeed
        val SCORE_SPEED_INV: Double
            get() = RatingModel.config.scoreSpeedInv
        const val STAT_COUNT = 10
        // average score to sd:
        val SD_A: Double
            get() = RatingModel.config.scoreSdSlope
        val SD_B: Double
            get() = RatingModel.config.scoreSdIntercept
        var H = java.lang.Double.NaN
        fun reset() {
            trackCurrent = false
            H = RatingModel.config.initialHomeAdvantage
        }
    }
}
