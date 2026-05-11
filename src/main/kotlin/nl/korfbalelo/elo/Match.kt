package nl.korfbalelo.elo

import java.io.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

data class Match(
    var home: String,
    var away: String,
    var homeScore: Int,
    var awayScore: Int,
    override val date: LocalDate = LocalDate.now(),
) :
    Comparable<Match>, Serializable, RankingEvent() {
    var pHome: Double = 0.0
    var pDraw: Double = 0.0
    var pAway: Double = 0.0
    var guessHome: Int = 0
    var guessAway: Int = 0
    var homeDiff: Double = 0.0
    var awayDiff: Double = 0.0
    var homeRating: Double = 0.0
    var awayRating: Double = 0.0
    var homeRd: Int = 0
    var awayRd: Int = 0
    var actualHome: Team? = null
    var actualAway: Team? = null
    var special: Boolean = false

    override fun run() = RankingNew.doMatch(this).also {
        if (ApplicationNew.log) ApplicationNew.matches[Triple(date, home, away)] = this
    }

    override fun compareTo(other: Match): Int {
        return date.compareTo(other.date)
    }
    fun format() = listOf(
        date.format(DateTimeFormatter.ISO_DATE),
        home,
        away,
        homeScore,
        awayScore
    ).joinToString(",")
    fun formatFixture() = listOf(
        date.format(DateTimeFormatter.ISO_DATE),
        home,
        away
    ).joinToString(",")
}
