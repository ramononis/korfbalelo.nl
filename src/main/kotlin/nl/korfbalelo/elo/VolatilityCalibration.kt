package nl.korfbalelo.elo

import com.google.gson.reflect.TypeToken
import nl.korfbalelo.elo.infrastructure.FileEventCatalog
import java.io.File
import java.time.LocalDate
import kotlin.math.sqrt

// AI generated: deterministic staged calibration, retaining full settings for every replay.
object VolatilityCalibration {
    data class Experiment(val id: String, val family: String, val config: RatingModelBenchmarkConfig)
    data class Result(val experiment: Experiment, val all: PredictionBenchmarkSummary,
        val recent: PredictionBenchmarkSummary, val sigmaMin: Double, val sigmaMedian: Double,
        val sigmaMax: Double, val sigmaGamesCorrelation: Double)

    @JvmStatic
    fun main(args: Array<String>) {
        val output = File(System.getProperty("elo.benchmark.output", "build/volatility-calibration"))
        output.mkdirs()
        File(output, "failures.jsonl").delete()
        ApplicationNew.log = false
        if (System.getProperty("elo.benchmark.fittedStarts", "false") == "false") ApplicationNew.startRatings.clear()
        val events = FileEventCatalog().loadAllEvents()
        val window = BenchmarkWindow.fromEvents(events)
        val input = System.getProperty("elo.benchmark.configs")?.let(::File)
        val seeds: List<Experiment> = input?.let {
            gson.fromJson(it.readText(), object : TypeToken<List<Experiment>>() {}.type)
        } ?: emptyList()
        val experiments = when {
            "search" in args -> search()
            "refine" in args -> listOf(baseline()) + seeds.flatMap(::refine)
            else -> seeds
        }.distinctBy { gson.toJson(it.config) }
        require(experiments.isNotEmpty())
        File(output, "configs.json").writeText(gson.toJson(experiments))
        val results = mutableListOf<Result>()
        File(output, "summary.csv").bufferedWriter().use { writer ->
            writer.appendLine("id,family,matches,brier,logLoss,marginMae,teamScoreMae,wdlAccuracy,weightedMarginAccuracy,recentMatches,recentBrier,recentLogLoss,recentMarginMae,recentTeamScoreMae,sigmaMin,sigmaMedian,sigmaMax,sigmaGamesCorrelation,marginGaussianNll,marginCoverage95,configJson")
            for ((index, experiment) in experiments.withIndex()) {
                val result = try {
                    RatingModelBenchmark.runVariant(experiment.config, window, events).also {
                        check(it.prediction.brier.isFinite() && it.prediction.logLoss.isFinite() &&
                            it.prediction.marginMae.isFinite() && it.prediction.teamScoreMae.isFinite())
                    }
                } catch (failure: RuntimeException) {
                    if (experiment.family == "baseline") throw failure
                    // AI generated: unstable parameter settings fail the full replay; never drop matches.
                    File(output, "failures.jsonl").appendText(gson.toJson(mapOf(
                        "experiment" to experiment, "type" to failure.javaClass.simpleName,
                        "message" to failure.message)) + "\n")
                    println("${index + 1}/${experiments.size} ${experiment.id} FAILED: ${failure.message}")
                    continue
                }
                val recent = combined(PredictionBenchmark.byDate.filterKeys { !it.isBefore(LocalDate.of(2012, 8, 1)) }
                    .values.map { it.summary() }, result.prediction)
                val teams = RankingNew.ranking.values.filter { it.origins == 1 &&
                    it.lastMatchDate?.isBefore(window.second.minusYears(2)) == false && it.games > 0 }
                val sigmas = teams.map { it.rv }.sorted()
                val correlation = correlation(teams.map { it.games.toDouble() }, teams.map { it.rv })
                val r = Result(experiment, result.prediction, recent,
                    sigmas.first(), sigmas[sigmas.size / 2], sigmas.last(), correlation)
                results += r
                val p = r.all
                writer.appendLine((listOf(experiment.id, experiment.family, p.matches, p.brier, p.logLoss,
                    p.marginMae, p.teamScoreMae, p.wdlAccuracy, p.weightedMarginAccuracy,
                    recent.matches, recent.brier, recent.logLoss, recent.marginMae, recent.teamScoreMae,
                    r.sigmaMin, r.sigmaMedian, r.sigmaMax, r.sigmaGamesCorrelation, p.marginGaussianNll, p.marginCoverage95).map { it.toString() } +
                    ("\"" + gson.toJson(experiment.config).replace("\"", "\"\"") + "\"")).joinToString(","))
                writer.flush()
                println("${index + 1}/${experiments.size} ${experiment.id} ${p.compactLine()} sigma=${r.sigmaMin}/${r.sigmaMedian}/${r.sigmaMax}")
                if ("evaluate" in args) {
                    File(output, "${experiment.id}-by-date.csv").bufferedWriter().use { dates ->
                        dates.appendLine("date,matches,brier,logLoss,marginMae,teamScoreMae,marginGaussianNll,marginCoverage95")
                        for ((date, stats) in PredictionBenchmark.byDate) {
                            val s = stats.summary()
                            dates.appendLine(listOf(date, s.matches, s.brier, s.logLoss, s.marginMae, s.teamScoreMae, s.marginGaussianNll, s.marginCoverage95).joinToString(","))
                        }
                    }
                    File(output, "${experiment.id}-teams.csv").bufferedWriter().use { teamsFile ->
                        teamsFile.appendLine("name,created,lastMatch,games,origins,rating,rd,sigma")
                        for (team in RankingNew.ranking.values.sortedBy { it.name }) {
                            teamsFile.appendLine((listOf("\"" + team.name.replace("\"", "\"\"") + "\"",
                                team.created, team.lastMatchDate, team.games, team.origins, team.rating, team.rd, team.rv)).joinToString(","))
                        }
                    }
                }
            }
        }
        // AI generated: evaluation is descriptive and must never select parameters on later data.
        if ("evaluate" in args) return
        val base = results.firstOrNull { it.experiment.family == "baseline" } ?: results.first()
        val selected = mutableListOf<Experiment>()
        for (family in listOf("shared", "goal-margin")) {
            val candidates = results.filter { it.experiment.family == family }
            if (candidates.isEmpty()) continue
            val probability = candidates.sortedBy { objective(it, base) }.take(2)
            val safe = candidates.filter { it.all.marginMae <= base.all.marginMae * 1.002 &&
                it.all.teamScoreMae <= base.all.teamScoreMae * 1.002 &&
                it.recent.marginMae <= base.recent.marginMae * 1.005 }
                .sortedBy { objective(it, base) }.take(2)
            selected += (probability + safe).map { it.experiment }
        }
        File(output, "selected.json").writeText(gson.toJson(selected.distinctBy { gson.toJson(it.config) }))
    }

    private fun objective(r: Result, b: Result): Double =
        0.2 * r.all.brier / b.all.brier + 0.2 * r.all.logLoss / b.all.logLoss +
            0.3 * r.recent.brier / b.recent.brier + 0.3 * r.recent.logLoss / b.recent.logLoss

    private fun baseline() = Experiment("baseline", "baseline", RatingModelBenchmarkConfig(
        RatingModelConfig.standard, ScoreRatingTweakConfig.fromSystemProperties(), ScoreSeasonalityConfig.standard))

    private fun search(): List<Experiment> {
        val base = baseline()
        val experiments = mutableListOf(base)
        for (mode in listOf(VolatilityMode.SHARED, VolatilityMode.GOAL_MARGIN)) {
            val family = if (mode == VolatilityMode.SHARED) "shared" else "goal-margin"
            for (sigma in listOf(0.045, 0.06, 0.075, 0.09)) {
                for (scale in listOf(280.0, 320.0, 360.0, 400.0)) {
                    for ((a, b) in listOf(0.12 to 1.5, 0.166 to 1.85, 0.22 to 2.2)) {
                        val model = base.config.model.copy(marginRatingScale = scale, scoreSdSlope = a, scoreSdIntercept = b,
                            volatility = VolatilityConfig(mode = mode, initial = sigma))
                        experiments += Experiment("$family-${experiments.size}", family, base.config.copy(model = model))
                    }
                }
            }
        }
        return experiments
    }

    private fun refine(seed: Experiment): List<Experiment> {
        val c = seed.config
        val m = c.model
        val v = m.volatility
        val models = listOf(m) +
            listOf(0.8, 1.2).map { m.copy(volatility = v.copy(initial = v.initial * it)) } +
            listOf(-40.0, 40.0).map { m.copy(marginRatingScale = m.marginRatingScale + it) } +
            listOf(-0.03, 0.03).map { m.copy(scoreSdSlope = m.scoreSdSlope + it) } +
            listOf(-0.4, 0.4).map { m.copy(scoreSdIntercept = m.scoreSdIntercept + it) } +
            listOf(60.0, 100.0, 170.0, 260.0).map { m.copy(scoreSpeedInv = it) } +
            listOf(2.0, 4.0, 8.0).map { m.copy(scoreLearningWeightCap = it) } +
            listOf(20.0, 35.0, 50.0, 65.0).map { m.copy(initialHomeAdvantage = it) } +
            listOf(0.0, 1.0 / 64, 1.0 / 16).map { m.copy(homeAdvantageSpeed = it) } +
            listOf(250.0, 350.0, 500.0).map { m.copy(rdMax = it) } +
            listOf(0.25, 0.5, 1.0).map { m.copy(initialRdFraction = it) } +
            if (v.mode == VolatilityMode.GOAL_MARGIN) {
                listOf(0.0, 1.0, 4.0, 16.0, 64.0, 256.0).map { m.copy(volatility = v.copy(learningRate = it)) } +
                    listOf(0.0, 90.0, 365.0, 730.0, 1825.0).map { m.copy(volatility = v.copy(halfLifeDays = it)) }
            } else emptyList()
        val configs = models.map { c.copy(model = it) } +
            c.copy(scoreRating = ScoreRatingTweakConfig()) +
            listOf(0.0003, 0.001, 0.003, 0.01).map { c.copy(scoreRating = c.scoreRating.copy(learningRate = it)) } +
            c.copy(scoreRating = c.scoreRating.copy(scaleByRd = true)) +
            listOf(0.001, 0.0025, 0.01, 0.02).map { c.copy(seasonality = c.seasonality.copy(learningRate = it)) } +
            listOf(3.0, 6.0).map { c.copy(seasonality = c.seasonality.copy(maxAdjustment = it)) } +
            listOf(730.0, 1825.0, 3650.0).map { c.copy(seasonality = c.seasonality.copy(halfLifeDays = it)) }
        return configs.distinct().mapIndexed { i, config -> Experiment("${seed.id}-r$i", seed.family, config) }
    }

    private fun combined(stats: List<PredictionBenchmarkSummary>, fallback: PredictionBenchmarkSummary): PredictionBenchmarkSummary {
        val n = stats.sumOf { it.matches }
        if (n == 0) return fallback
        fun average(value: (PredictionBenchmarkSummary) -> Double) = stats.sumOf { value(it) * it.matches } / n
        return fallback.copy(matches = n, brier = average { it.brier }, logLoss = average { it.logLoss },
            marginMae = average { it.marginMae }, teamScoreMae = average { it.teamScoreMae })
    }

    private fun correlation(x: List<Double>, y: List<Double>): Double {
        val mx = x.average(); val my = y.average()
        val xx = x.sumOf { (it - mx) * (it - mx) }; val yy = y.sumOf { (it - my) * (it - my) }
        return if (x.isEmpty() || y.maxOrNull()!! - y.minOrNull()!! < 1e-12 || xx * yy == 0.0) Double.NaN else x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / sqrt(xx * yy)
    }
}
