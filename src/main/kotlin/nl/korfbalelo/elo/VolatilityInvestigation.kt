package nl.korfbalelo.elo

import nl.korfbalelo.elo.application.RatingPipeline
import nl.korfbalelo.elo.domain.RatingRunWindow
import nl.korfbalelo.elo.infrastructure.FileEventCatalog
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// AI generated: experiments stay in the replay wrapper, leaving production updates unchanged.
object VolatilityInvestigation {
    private data class Variant(
        val name: String,
        val fixed: Double? = null,
        val halfLifeDays: Double? = null,
        val floor: Double? = null,
        val resetDate: LocalDate? = null,
        val binaryEvidence: Boolean = false,
    )

    private data class Counts(
        var updates: Long = 0,
        var decreases: Long = 0,
        var increases: Long = 0,
        var eligible: Long = 0,
        var balanced: Long = 0,
        var balancedIncreases: Long = 0,
        var residualRatioSum: Double = 0.0,
        var binaryResidualRatioSum: Double = 0.0,
        var growthEvidence: Long = 0,
    )

    private data class Before(val team: Team, val sigma: Double, val evidence: Evidence?)
    private data class Evidence(val expected: Double, val residualRatio: Double,
        val binaryResidualRatio: Double, val growth: Boolean, val binarySigma: Double)

    private class ObservedMatch(
        private val match: Match,
        private val variant: Variant,
        private val counts: Counts,
    ) : RankingEvent() {
        override val date = match.date
        override fun run() {
            if (match.homeScore < 0 || match.awayScore < 0) { match.run(); return }
            val home = RankingNew.ranking.getValue(RankingNew.aliases[match.home] ?: match.home)
            val away = RankingNew.ranking.getValue(RankingNew.aliases[match.away] ?: match.away)
            for (team in listOf(home, away)) {
                if (variant.fixed != null) team.rv = variant.fixed
                if (variant.halfLifeDays != null) {
                    val days = ChronoUnit.DAYS.between(team.lastDate ?: date, date).toDouble()
                    val retention = 0.5.pow(days / variant.halfLifeDays)
                    team.rv = exp(ln(0.06) + retention * (ln(team.rv) - ln(0.06)))
                }
            }
            val initialized = home.rating != Team.MAGIC_1500 && away.rating != Team.MAGIC_1500
            val sd = if (initialized) diffDistroBetween(home, away, date).second else Double.NaN
            val before = listOf(
                Before(home, home.rv, if (initialized) evidence(home, away,
                    match.homeScore - match.awayScore, Team.H, sd, date) else null),
                Before(away, away.rv, if (initialized) evidence(away, home,
                    match.awayScore - match.homeScore, -Team.H, sd, date) else null),
            )
            match.run()
            for ((team, sigma, e) in before) {
                counts.updates++
                if (team.rv < sigma) counts.decreases++
                if (team.rv > sigma) counts.increases++
                if (e != null) {
                    counts.eligible++
                    counts.residualRatioSum += e.residualRatio
                    counts.binaryResidualRatioSum += e.binaryResidualRatio
                    if (e.growth) counts.growthEvidence++
                    if (e.expected in 0.45..0.55) {
                        counts.balanced++
                        if (team.rv > sigma) counts.balancedIncreases++
                    }
                }
                if (variant.binaryEvidence && e != null) team.rv = e.binarySigma
                if (variant.fixed != null) team.rv = variant.fixed
                if (variant.floor != null) team.rv = max(variant.floor, team.rv)
            }
        }
    }

    private fun evidence(team: Team, opponent: Team, difference: Int, homeOffset: Double,
        sd: Double, date: LocalDate): Evidence {
        fun rating(t: Team) = Glicko2Rating(t.rating,
            Glicko2Calculator.ageRatingDeviation(t.rd, t.rv,
                ChronoUnit.DAYS.between(t.lastDate ?: date, date) / Team.RD_PERIOD_DAYS, Team.RD_MAX), t.rv)
        val own = rating(team)
        val other = rating(opponent)
        val g = 1 / sqrt(1 + 3 * (other.ratingDeviation / Glicko2Calculator.RATING_SCALE).pow(2) / PI.pow(2))
        val expected = 1 / (1 + exp(-g * (team.rating + homeOffset - opponent.rating) / Glicko2Calculator.RATING_SCALE))
        val score = ND(0.0, sd).cdf(difference.toDouble())
        val binaryScore = if (difference > 0) 1.0 else if (difference < 0) 0.0 else 0.5
        val v = 1 / (g * g * expected * (1 - expected))
        val delta = v * g * (score - expected)
        val binarySigma = Glicko2Calculator.update(own,
            Glicko2Game(other, binaryScore, homeOffset), Team.RD_MAX).rating.volatility
        return Evidence(expected, (score - expected).pow(2) / (expected * (1 - expected)),
            (binaryScore - expected).pow(2) / (expected * (1 - expected)),
            delta * delta > (own.ratingDeviation / Glicko2Calculator.RATING_SCALE).pow(2) + v + team.rv * team.rv,
            binarySigma)
    }

    private class Reset(override val date: LocalDate) : RankingEvent() {
        override fun run() { RankingNew.ranking.values.forEach { it.rv = 0.06 } }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // AI generated: this historical diagnostic compares against the original learned policy.
        if (System.getProperty("elo.model.profile") == null) System.setProperty("elo.model.profile", "legacy")
        val events = FileEventCatalog().loadAllEvents()
        val window = BenchmarkWindow.fromEvents(events)
        val output = File(System.getProperty("elo.benchmark.output", "build/volatility"))
        output.mkdirs()
        ApplicationNew.log = false
        if (System.getProperty("elo.benchmark.fittedStarts", "true") == "false") ApplicationNew.startRatings.clear()
        val allVariants = if ("baseline" in args) listOf(Variant("learned")) else listOf(
            Variant("learned"),
            *listOf(0.04, 0.05, 0.055, 0.06, 0.065, 0.075, 0.09).map { Variant("fixed-$it", fixed = it) }.toTypedArray(),
            *listOf(365.0, 1825.0, 3650.0).map { Variant("revert-$it", halfLifeDays = it) }.toTypedArray(),
            Variant("floor-0.055", floor = 0.055),
            Variant("reset-1970", resetDate = LocalDate.of(1970, 8, 30)),
            Variant("reset-2000", resetDate = LocalDate.of(2000, 8, 1)),
            Variant("binary-evidence", binaryEvidence = true),
        )
        // AI generated: selected on development data, with reset/binary controls for mechanism checks.
        val finalists = setOf("learned", "fixed-0.06", "fixed-0.075", "revert-1825.0",
            "reset-1970", "reset-2000", "binary-evidence")
        val variants = if ("finalists" in args) allVariants.filter { it.name in finalists } else allVariants
        File(output, "summary.csv").bufferedWriter().use { summary ->
            summary.appendLine("variant,from,to,matches,brier,logLoss,marginMae,teamScoreMae,wdlAccuracy,updates,decreases,increases,eligible,growthEvidence,balanced,balancedIncreases,residualRatio,binaryResidualRatio")
            for (variant in variants) {
                RatingModel.config = RatingModelConfig.fromSystemProperties()
                ScoreRatingTweak.config = ScoreRatingTweakConfig.fromSystemProperties()
                ScoreSeasonality.config = ScoreSeasonalityConfig.fromSystemProperties()
                PredictionBenchmark.range = window
                AccuracyTracker.ignoreMatchesBelow = LocalDate.MIN
                val counts = Counts()
                val observed = events.filter { !it.date.isAfter(window.second) }.map {
                    if (it is Match) ObservedMatch(it, variant, counts) else it
                }.toMutableList()
                variant.resetDate?.takeUnless { it.isAfter(window.second) }?.let { observed.add(Reset(it)) }
                RatingPipeline().run(observed.toSet(), RatingRunWindow(window.first, window.second),
                    emptySet(), false, rebalanceRatings = true, onDate = {})
                val p = PredictionBenchmark.accuracy.summary()
                summary.appendLine(listOf(variant.name, window.first, window.second, p.matches,
                    p.brier, p.logLoss, p.marginMae, p.teamScoreMae, p.wdlAccuracy,
                    counts.updates, counts.decreases, counts.increases, counts.eligible, counts.growthEvidence,
                    counts.balanced, counts.balancedIncreases, counts.residualRatioSum / counts.eligible,
                    counts.binaryResidualRatioSum / counts.eligible).joinToString(","))
                summary.flush()
                println("${variant.name}: ${p.compactLine()} | sigma decreases=${counts.decreases}/${counts.updates}")
                writeTeams(File(output, "${variant.name}-teams.csv"))
                if (PredictionBenchmark.byDate.isNotEmpty()) {
                    File(output, "${variant.name}-by-date.csv").bufferedWriter().use { writer ->
                        writer.appendLine("date,matches,brier,logLoss,marginMae,teamScoreMae")
                        for ((date, stats) in PredictionBenchmark.byDate) {
                            val s = stats.summary()
                            writer.appendLine(listOf(date, s.matches, s.brier, s.logLoss, s.marginMae, s.teamScoreMae).joinToString(","))
                        }
                    }
                }
            }
        }
    }

    private fun writeTeams(file: File) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("name,created,firstMatch,lastMatch,games,rating,rd,sigma,origins")
            for (t in RankingNew.ranking.values.sortedBy { it.name }) {
                val name = "\"" + t.name.replace("\"", "\"\"") + "\""
                writer.appendLine(listOf(name, t.created, t.firstMatchDate, t.lastMatchDate,
                    t.games, t.rating, t.rd, t.rv, t.origins).joinToString(","))
            }
        }
    }
}
