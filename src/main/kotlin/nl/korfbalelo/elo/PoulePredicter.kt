package nl.korfbalelo.elo

import nl.korfbalelo.elo.RankingNew.ranking
import nl.korfbalelo.elo.SeasonPredicter.loser
import nl.korfbalelo.elo.SeasonPredicter.winner
import nl.korfbalelo.mijnkorfbal.GoalsStats
import nl.korfbalelo.mijnkorfbal.Standing
import nl.korfbalelo.mijnkorfbal.StandingPenalties
import nl.korfbalelo.mijnkorfbal.StandingStats
import org.apache.commons.math3.special.Erf
import org.apache.commons.math3.util.FastMath
import java.time.LocalDate
import java.util.SplittableRandom
import java.util.random.RandomGenerator
import kotlin.concurrent.getOrSet
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days

class PoulePredicter(
    val pouleName: String,
    val teamsToPenalty: Map<String, Int>,
    matchesParam: List<Match>,
    val date: LocalDate?
) {
    var oneway = SeasonPredicter.doOutdoor && teamsToPenalty.size == 7
    val teamNames = teamsToPenalty.keys.toList()
    val matches = matchesParam
        .map(::resolveKnownResult)
        .filter { (it.home in teamNames && it.away in teamNames) || it.special }

    val teams = teamNames.map {
        ranking[it] ?: error("missing ranking for team '$it' in poule '$pouleName'")
    }.toTypedArray()

    val n = teamNames.size
    val baseMatches: Array<Triple<Int, Int, LocalDate>?> = Array<Triple<Int, Int, LocalDate>?>(teamNames.size * teamNames.size) { null }
        .also {
            if (matches.isEmpty()) {
                for ((i, triple) in it.withIndex()) {
                    val a = i % n
                    val b = i / n
                    if (a == b) continue
                    if (!oneway || a > b)
                        it[i] = -1 to -1 ot (date ?: LocalDate.now())
                }
            } else {
                for (m in matches) {
                    if (m.special) continue
                    val hI = teamNames.indexOf(m.home)
                    val aI = teamNames.indexOf(m.away)
                    val i = hI * n + aI
                    if (i < 0) {
                        if (m.home in ignoredTeams || m.away in ignoredTeams)
                            continue
                        error(m.toString())
                    }
                    it[i] = (if (date == null || date >= m.date) {
                        m.homeScore to m.awayScore
                    } else -1 to -1) ot m.date
                }
            }
        }

    val order: List<Pair<LocalDate, Array<Int>>> = baseMatches.mapIndexedNotNull { i, t ->
        t?.let { (hScore, aScore, date) ->
            if (hScore == -1) date to i else null
        }
    }
        .groupBy { it.first }
        .mapValues { (date, ix) -> ix.map { it.second }.toTypedArray() }
        .map { it.key to it.value }
        .sortedBy { it.first }

    val startPoints = IntArray(n)
    val startBalance = IntArray(n)
    val startScored = IntArray(n)

    private fun resolveKnownResult(match: Match): Match {
        if (match.homeScore != -1 || match.awayScore != -1) {
            return match
        }
        val actual = ApplicationNew.matches[Triple(match.date, match.home, match.away)] ?: return match
        return match.copy(
            homeScore = actual.homeScore,
            awayScore = actual.awayScore,
        ).also {
            it.special = match.special
        }
    }

    init {
        val l = baseMatches.size
        for (i in 0 until l) {
            val h = i / n
            val a = i % n
            if (h == a) continue
            val m = baseMatches[i]


            m?.pts()?.let {
                if (m.first != -1) {
                    startPoints[h] += it
                    startPoints[a] += 2 - it
                    startBalance[h] += m.bal()
                    startBalance[a] += -m.bal()
                    startScored[h] += m.first
                    startScored[a] += m.second
                }
            }
        }
        teamNames.forEachIndexed { tI, tS ->
            startPoints[tI] -= teamsToPenalty.getValue(tS)
        }
    }

    private val executorLocal = ThreadLocal<Executor>()
    val executor get() = executorLocal.getOrSet { Executor() }


    context(_: RandomGenerator)
    fun simulate() {
        executor.run()
    }

    fun currentStanding(): Any {
        val snapshotDate = date
        val executor = Executor()
        executor.points = startPoints.copyOf()
        executor.balance = startBalance.copyOf()
        executor.scored = startScored.copyOf()
        executor.useRatings = true
        val result = if (matches.isEmpty()) {
            teamNames.indices.map {
                ranking[teamNames[it]]!! to Triple(
                    startPoints[it],
                    startBalance[it],
                    startScored[it]
                )
            }
        } else with(SplittableRandom(0L)) {
            executor.rank((0 until n).toList(), startPoints) { duplicates ->
                executor.resolvePointsTie(duplicates)
            }.map {
                ranking[teamNames[it]]!! to Triple(
                    startPoints[it],
                    startBalance[it],
                    startScored[it]
                )
            }
        }
        val fixtures = matches.filter { match ->
            match.homeScore == -1
                || (
                    snapshotDate != null &&
                        (match.date.isAfter(snapshotDate) || (match.special && match.date == snapshotDate))
                    )
        }
        fixtures.forEach { m ->
            val home = ranking[m.home]!!
            val away = ranking[m.away]!!
            val diffDistro = diffDistroBetween(home, away)
            val distro = distroBetween(home, away)
            m.pHome = 1.0 - diffDistro.cdf(0.5)
            m.pDraw = diffDistro.cdf(0.5) - diffDistro.cdf(-0.5)
            m.pAway = diffDistro.cdf(-0.5)
            m.guessHome = distro.first.first.roundToInt()
            m.guessAway = distro.second.first.roundToInt()
            m.homeRating = home.rating
            m.awayRating = away.rating
        }
        val results = matches.filter { it.homeScore != -1 && (snapshotDate == null || !it.date.isAfter(snapshotDate)) }
        results.forEach { m ->
            ApplicationNew.matches[Triple(m.date, m.home, m.away)]?.let { snapshot ->
                m.pHome = snapshot.pHome
                m.pDraw = snapshot.pDraw
                m.pAway = snapshot.pAway
                m.guessHome = snapshot.guessHome
                m.guessAway = snapshot.guessAway
                m.homeDiff = snapshot.homeDiff
                m.awayDiff = snapshot.awayDiff
                m.homeRating = snapshot.homeRating
                m.awayRating = snapshot.awayRating
                m.homeRd = snapshot.homeRd
                m.awayRd = snapshot.awayRd
                m.actualHome = snapshot.actualHome
                m.actualAway = snapshot.actualAway
            }
        }
        return mapOf(
            "results" to results,
            "fixtures" to fixtures,
            "standing" to result.mapIndexed { index, (t, stats) ->
                val ownMatches = results.filter { (it.home == t.name || it.away == t.name) && !it.special }
                Standing(
                    nl.korfbalelo.mijnkorfbal.Team(t.name),
                    StandingStats(
                        StandingPenalties(teamsToPenalty.getValue(t.name)),
                        index + 1,
                        stats.first,
                        GoalsStats(stats.third, stats.third - stats.second),
                        ownMatches.count { it.winner() == t.name },
                        ownMatches.count { it.homeScore == it.awayScore },
                        ownMatches.count { it.loser() == t.name }
                    )
                )
            }
        )
    }

    inner class Executor {
        val matches = baseMatches.map { it?.copy() }.toTypedArray()
        val teamsCopy = teams.copyOf()
        var useRatings = false
        lateinit var points: IntArray
        lateinit var balance: IntArray
        lateinit var scored: IntArray
        lateinit var result: List<Pair<Team, Triple<Int, Int, Int>>>

        context(_: RandomGenerator)
        fun doMatch(m: Int, date: LocalDate) {
            val h = m / n
            val a = m % n
            val (sh, sa) = distroBetween(teamsCopy[h].also{it.sampleRating(date)}, teamsCopy[a].also{it.sampleRating(date)}, oneway).sample()
            val result = sh to sa ot date
            matches[m] = result
            val r = result.pts()
            points[h] += r
            points[a] += 2 - r
            balance[h] += sh - sa
            balance[a] += sa - sh
            scored[h] += sh
            scored[a] += sa
        }

        context(_: RandomGenerator)
        fun run() {
            points = startPoints.copyOf()
            balance = startBalance.copyOf()
            scored = startScored.copyOf()
            teams.forEachIndexed { index, team ->
                teamsCopy[index] = Team(team.name, "JE MOEDER", team.rating).apply {
                    lastDate = team.lastDate
                    rd = team.rd
                    rv = team.rv
                    averageScore = team.averageScore
                }
            }
            for ((date, ms) in order) {
                for (m in ms) {
                    doMatch(m, date)
                }
            }
            result = rank(
                (0 until n).toList(),
                points
            ) { duplicates ->
                resolvePointsTie(duplicates)
            }.map {
                teamsCopy[it] to Triple(
                    points[it],
                    balance[it],
                    scored[it]
                )
            }
        }

        context(_: RandomGenerator)
        fun rank(
            teams: List<Int>,
            scores: IntArray,
            resolver: context(RandomGenerator) (duplicates: List<Int>) -> List<Int>
        ): List<Int> {
            var highest = Int.MIN_VALUE
            var prevHighest = Int.MAX_VALUE
            val duplicates = mutableListOf<Int>()
            val ranking = mutableListOf<Int>()
            var nRanked = 0
            while (nRanked < teams.size) {
                for (ti in teams) {
                    val s = scores[ti]
                    if (s in (highest + 1) until prevHighest) {
                        duplicates.clear()
                        highest = s
                        duplicates.add(ti)
                    } else if (s == highest) {
                        duplicates.add(ti)
                    }
                }
                if (duplicates.size > 1) {
                    ranking.addAll(resolver(duplicates))
                } else if (duplicates.size == 1) {
                    ranking.add(duplicates.first())
                } else {
                    error("could not rank remaining teams $teams with scores ${scores.toList()}")
                }
                nRanked += duplicates.size
                duplicates.clear()
                prevHighest = highest
                highest = Int.MIN_VALUE
            }
            return ranking
        }

        context(random: RandomGenerator)
        fun resolvePointsTie(ints: List<Int>): List<Int> {
            val subPoints = IntArray(n)
            val subScored = IntArray(n)
            val subBalance = IntArray(n)
            for (h in ints) {
                for (a in ints) {
                    val m = matches[h * n + a]
                    if (h != a) {
                        val pts = m?.pts() ?: continue
                        subPoints[h] += pts
                        subPoints[a] += 2 - pts
                        val sh = m.first
                        val sa = m.second
                        subBalance[h] += sh - sa
                        subBalance[a] += sa - sh
                        subScored[h] += sh
                        subScored[a] += sa
                    }
                }
            }
            fun resolveSubScoredTie(subInts: List<Int>): List<Int> {
                return if (subInts.size == ints.size) {
                    rank(subInts, balance) { duplicates ->
                        resolveBalanceTie(duplicates)
                    }
                } else {
                    resolvePointsTie(subInts)
                }
            }

            fun resolveSubBalanceTie(subInts: List<Int>): List<Int> {
                return if (subInts.size == ints.size) {
                    rank(subInts, subScored) { duplicates ->
                        resolveSubScoredTie(duplicates)
                    }
                } else {
                    resolvePointsTie(subInts)
                }
            }

            fun resolveSubPointsTie(subInts: List<Int>): List<Int> {
                return if (subInts.size == ints.size) {
                    rank(subInts, subBalance) { duplicates ->
                        resolveSubBalanceTie(duplicates)
                    }
                } else {
                    resolvePointsTie(subInts)
                }
            }
            return rank(ints, subPoints) { duplicates ->
                resolveSubPointsTie(duplicates)
            }
        }

        context(random: RandomGenerator)
        private fun resolveBalanceTie(ints: List<Int>): List<Int> {
            return rank(ints, scored) { duplicates ->
                resolveScoredTie(duplicates)
            }
        }

        context(random: RandomGenerator)
        private fun resolveScoredTie(ints: List<Int>): List<Int> {
            if (useRatings) {
                return rank(ints, teamsCopy.map { (it.rating * 10000).toInt() }.toIntArray()) { tiedByRating ->
                    tiedByRating.sortedBy { teamNames[it] }
                }
            }
            val scores = IntArray(n)
            val currDuplicates = ints.toMutableList()
            var score = n
            while (currDuplicates.size > 1) {
                val strengths = currDuplicates.map { teamsCopy[it].rating.pow(ints.size) }
                val sum = strengths.sum()
                val normalized = strengths.map { it / sum }
                var current = 0.0
                val roll = random.nextDouble()
                var index = 0
                for (d in normalized) {
                    current += d
                    if (roll <= current) {
                        scores[currDuplicates[index]] += score--
                        currDuplicates.removeAt(index)
                        break
                    }
                    index++
                }
            }
            return rank(ints, scores) { tiedByRandomScore ->
                tiedByRandomScore.sortedBy { teamNames[it] }
            }
        }
    }

    companion object {
        val SQRT2 = FastMath.sqrt(2.0)
        var pointsTie = IntArray(8)
        var subPointsTie = IntArray(8)
        var subBalanceTie = IntArray(8)
        var subScoredTie = IntArray(8)
        var balanceTie = IntArray(8)
        var scoredTie = IntArray(8)

        fun reset() {
            pointsTie = IntArray(8)
            subPointsTie = IntArray(8)
            subBalanceTie = IntArray(8)
            subScoredTie = IntArray(8)
            balanceTie = IntArray(8)
            scoredTie = IntArray(8)
        }
    }
}
fun Double.shouldBe(other: Double) = (this - other).absoluteValue < 1e-3 || error("expected $other but was $this")

infix fun <A,B,C> Pair<A, B>.ot(third: C) = Triple(first, second, third)
typealias R = Triple<Int, Int, LocalDate?>
typealias ND = Pair<Double, Double>
val SQRT2 = FastMath.sqrt(2.0)
fun ND.cdf(x: Double): Double {
    val dev: Double = x - first
    return if (FastMath.abs(dev) > 40 * second) {
        if (dev < 0) 0.0 else 1.0
    } else 0.5 * Erf.erfc(-dev / (second * SQRT2))
}
fun R.pts() = (first - second).sign + 1
fun R.bal() = (first - second)
typealias Matchup = Pair<ND, ND>
context(random: RandomGenerator)
fun ND.sample() = second * random.nextGaussian() + first

context(_: RandomGenerator)
fun Matchup.sample() = first.sample().roundToInt() to second.sample().roundToInt()

val ignoredTeams = listOf("Ready '60")
