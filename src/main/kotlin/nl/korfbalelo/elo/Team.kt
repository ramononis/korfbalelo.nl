package nl.korfbalelo.elo

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Comparator
import java.util.SortedSet
import java.util.random.RandomGenerator
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

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
                error("WTF")
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
    var rv: Double = 0.06
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
        val phiPre = rd / D
        val periods = ChronoUnit.DAYS.between(lastDate ?: date, date) / RD_PERIOD_DAYS
        rd = min(RD_MAX / D, sqrt(phiPre * phiPre + periods * rv * rv)) * D
        lastDate = date
    }

    fun newRating(
        opponent: Team,
        _for: Int,
        against: Int,
        atHome: Boolean,
        match: Match,
        diffDistro: ND
    ): List<Double> {
        val diff = _for - against
        val sumAve = (averageScore + opponent.averageScore) / 2
        val s = 1.0 - diffDistro.cdf(diffDistro.first - diff.toDouble())
        val expScore = sumAve + diffDistro.first * 0.5

        val mu1 = (rating - 1500.0) / D
        val phi1 = rd / D
        var mu2 = (opponent.rating - 1500.0) / D
        val phi2 = opponent.rd / D
        mu2 -= when (atHome) {
            true -> H
            false -> -H
        } / D

        val sig1 = rv

        val g = 1.0 / sqrt(1.0 + 3.0 * phi2 * phi2 / PI / PI)
        val dr = mu1 - mu2
        val bigE = 1.0 / (1.0 + exp(-g * dr))

        val v = 1 / (g * g * bigE * (1.0 - bigE))
        val delta = v * g * (s - bigE)

        val a = ln(sig1 * sig1)
        fun f(x: Double) =
            exp(x) * (delta * delta - phi1 * phi1 - v - exp(x)) / (2.0 * (phi1 * phi1 + v + exp(x)).pow(2)) - (x - a) / (TAU * TAU)

        var bigA = a
        var bigB = if (delta * delta > phi1 * phi1 + v) ln(delta * delta - phi1 * phi1 - v)
        else run {
            var k = 1
            while (f(a - k * TAU) < 0.0) {
                k++
            }
            a - k * TAU
        }
        var fA = f(bigA)
        var fB = f(bigB)
        while ((bigB - bigA).absoluteValue > EPSILON) {
            val bigC = bigA + (bigA - bigB) * fA / (fB - fA)
            val fC = f(bigC)
            if (fC * fB <= 0.0) {
                bigA = bigB
                fA = fB
            } else {
                fA /= 2.0
            }
            bigB = bigC
            fB = fC
        }
        val sigPrime = exp(bigA / 2.0)
        val phiPrime = min(RD_MAX / D, 1.0 / sqrt(1.0 / phi1 / phi1 + 1.0 / v))
        val muD = phiPrime * phiPrime * (g * (s - bigE))
        if (muD.isNaN()) {
            error("WTF")
        }
        PredictionBenchmark.recordRatingDelta(muD.absoluteValue)
        val muPrime = mu1 + muD
        val rPrime = D * muPrime + 1500.0
        val rdPrime = D * phiPrime

        if (trackCurrent) currentDiff += rPrime - rating
        val newAverageScore = averageScore + (_for - expScore) / SCORE_SPEED_INV * v * g

        if (ApplicationNew.log) {
            if (atHome) {
                match.pHome = 1.0 - diffDistro.cdf(0.5)
                match.pDraw = diffDistro.cdf(0.5) - diffDistro.cdf(-0.5)
                match.pAway = diffDistro.cdf(-0.5)
                match.guessHome = expScore.roundToInt()
                match.guessAway = (expScore - diffDistro.first).roundToInt()
                if (match.guessHome == match.guessAway) {
                    match.guessHome += dr.sign.toInt()
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
            H += delta * H_SPEED
        }

        games++

        check(rPrime != MAGIC_1500)
        return listOf(rPrime, rdPrime, sigPrime, newAverageScore)
    }

    fun setNewRating(newRating: List<Double>) {
        rating = newRating[0]
        rd = newRating[1]
        rv = newRating[2]
        averageScore = max(0.5, newRating[3])
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
        const val D = 173.7178 // non-tweakable
        const val RD_MAX = 350.0
        const val RD_PERIOD_DAYS = 1.0
        const val H_SPEED = 1.0 / 64.0
        const val SCORE_SPEED_INV = 100.0
        const val STAT_COUNT = 10
        // average score to sd:
        const val SD_A = 0.166
        const val SD_B = 1.85
        var H = java.lang.Double.NaN
        val TAU = 0.6 // volatility constant
        const val EPSILON = 0.000001 // non-tweakable
        fun reset() {
            trackCurrent = false
            H = 35.0
        }
    }
}
