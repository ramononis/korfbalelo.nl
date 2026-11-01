package nl.korfbalelo.elo

import nl.korfbalelo.elo.application.RatingPipeline
import nl.korfbalelo.elo.domain.RatingRunWindow
import nl.korfbalelo.elo.infrastructure.FileEventCatalog
import java.io.File
import java.time.LocalDate
import kotlin.time.measureTime

// AI generated
object RatingModelBenchmark {
    private val eventCatalog = FileEventCatalog()

    @JvmStatic
    fun main(args: Array<String>) {
        val events = eventCatalog.loadAllEvents()
        val window = BenchmarkWindow.fromEvents(events)
        ApplicationNew.log = false

        // AI generated: an honest sensitivity check excludes the outcome-fitted spawn priors.
        if (System.getProperty("elo.benchmark.fittedStarts", "true") == "false") {
            ApplicationNew.startRatings.clear()
        }
        val baseline = RatingModelBenchmarkConfig(RatingModelConfig.fromSystemProperties(),
            ScoreRatingTweakConfig.fromSystemProperties(), ScoreSeasonalityConfig.fromSystemProperties())
        val configs = if ("calibrate" in args) calibrationConfigs(baseline) else listOf(baseline)
        println("Running ${configs.size} rating-model variants over ${window.first}..${window.second}")
        val results = mutableListOf<RatingModelBenchmarkResult>()
        val elapsed = measureTime {
            configs.forEachIndexed { index, config ->
                val result = runVariant(config, window, events)
                results += result
                println("${index + 1}/${configs.size} ${result.oneLine()}")
            }
        }

        if (results.size > 1) {
            val byWeighted = results.sortedByDescending { it.prediction.weightedMarginAccuracy }
            val byBrier = results.sortedBy { it.prediction.brier }
            val byMargin = results.sortedBy { it.prediction.marginMae }
            val byTeamScore = results.sortedBy { it.prediction.teamScoreMae }

            println()
            println("Best weighted-margin accuracy:")
            byWeighted.take(15).forEachIndexed { index, result -> println("${index + 1}. ${result.oneLine()}") }
            println()
            println("Best Brier score:")
            byBrier.take(10).forEachIndexed { index, result -> println("${index + 1}. ${result.oneLine()}") }
            println()
            println("Best margin MAE:")
            byMargin.take(10).forEachIndexed { index, result -> println("${index + 1}. ${result.oneLine()}") }
            println()
            println("Best team-score MAE:")
            byTeamScore.take(10).forEachIndexed { index, result -> println("${index + 1}. ${result.oneLine()}") }
        }

        writeCsv(results, window)
        println()
        println("Finished in $elapsed")
    }

    internal fun runVariant(
        config: RatingModelBenchmarkConfig,
        window: Pair<LocalDate, LocalDate>,
        events: Set<RankingEvent>,
    ): RatingModelBenchmarkResult {
        RatingModel.config = config.model
        ScoreRatingTweak.config = config.scoreRating
        ScoreSeasonality.config = config.seasonality
        PredictionBenchmark.range = window
        // AI generated: evaluation windows must retain all earlier training matches.
        AccuracyTracker.ignoreMatchesBelow = LocalDate.MIN
        RatingPipeline().run(
            events = events.filter { !it.date.isAfter(window.second) }.toSet(),
            window = RatingRunWindow(window.first, window.second),
            activeTeams = emptySet(),
            logEnabled = false,
            rebalanceRatings = config.rebalanceRatings,
            onDate = {},
        )
        return RatingModelBenchmarkResult(
            config = config,
            prediction = PredictionBenchmark.accuracy.summary(),
            scoreRating = ScoreRatingTweak.summary(),
            seasonality = ScoreSeasonality.summary(),
            byDate = PredictionBenchmark.byDate.mapValues { it.value.summary() },
        )
    }

    // AI generated: local sensitivity search around the calibrated shared-growth model.
    private fun calibrationConfigs(base: RatingModelBenchmarkConfig): List<RatingModelBenchmarkConfig> {
        val m = base.model
        return (listOf(base) +
            listOf(0.8, 1.2).map { base.copy(model = m.copy(rdGrowthPerSqrtDay = m.rdGrowthPerSqrtDay * it)) } +
            listOf(300.0, 340.0).map { base.copy(model = m.copy(marginRatingScale = it)) } +
            listOf(0.10, 0.14).map { base.copy(model = m.copy(scoreSdSlope = it)) } +
            listOf(1.7, 2.1).map { base.copy(model = m.copy(scoreSdIntercept = it)) } +
            listOf(80.0, 130.0).map { base.copy(model = m.copy(scoreSpeedInv = it)) } +
            listOf(400.0, 500.0).map { base.copy(model = m.copy(rdMax = it)) } +
            listOf(0.5, 1.0).map { base.copy(model = m.copy(initialRdFraction = it)) } +
            listOf(0.0025, 0.01).map { base.copy(seasonality = base.seasonality.copy(learningRate = it)) }
        ).distinct()
    }

    private fun writeCsv(results: List<RatingModelBenchmarkResult>, window: Pair<LocalDate, LocalDate>) {
        val file = File(System.getProperty("elo.benchmark.output", "build/rating-model-benchmark.csv"))
        file.absoluteFile.parentFile.mkdirs()
        if (results.any { it.byDate.isNotEmpty() }) {
            File(file.path.removeSuffix(".csv") + "-by-date.csv").bufferedWriter().use { writer ->
                writer.appendLine("variant,date,matches,brier,logLoss,marginMae,teamScoreMae")
                results.forEachIndexed { index, result ->
                    result.byDate.forEach { (date, stats) ->
                        writer.appendLine(listOf(index, date, stats.matches, stats.brier, stats.logLoss,
                            stats.marginMae, stats.teamScoreMae).joinToString(","))
                    }
                }
            }
        }
        file.writeText(
            buildString {
                appendLine(
                    listOf(
                        "evaluationFrom",
                        "evaluationTo",
                        "fittedStarts",
                        "initialHomeAdvantage",
                        "homeAdvantageSpeed",
                        "scoreSpeedInv",
                        "scoreSdSlope",
                        "scoreSdIntercept",
                        "marginRatingScale",
                        "rdMax",
                        "rdGrowthPerSqrtDay",
                        "initialRdFraction",
                        "minAverageScore",
                        "scoreLearningWeightCap",
                        "scoreRatingMode",
                        "scoreRatingLearningRate",
                        "scoreRatingMaxAdjustment",
                        "scoreRatingMinGames",
                        "scoreRatingMaxRatingPerGoal",
                        "scoreRatingScaleByRd",
                        "scoreSeasonalityMode",
                        "scoreSeasonalityLearningRate",
                        "scoreSeasonalityMaxAdjustment",
                        "scoreSeasonalityHalfLifeDays",
                        "rebalanceRatings",
                        "matches",
                        "weightedMarginAccuracy",
                        "wdlAccuracy",
                        "brier",
                        "logLoss",
                        "marginMae",
                        "teamScoreMae",
                        "marginGaussianNll",
                        "marginCoverage95",
                        "exactScoreAccuracy",
                        "scoreRatingAverageAdjustment",
                        "seasonalityAverageOffset",
                        "seasonalityMonthOffsets",
                    ).joinToString(",")
                )
                results.forEach { result ->
                    val model = result.config.model
                    val score = result.config.scoreRating
                    val seasonality = result.config.seasonality
                    appendLine(
                        listOf(
                            window.first,
                            window.second,
                            System.getProperty("elo.benchmark.fittedStarts", "true"),
                            model.initialHomeAdvantage,
                            model.homeAdvantageSpeed,
                            model.scoreSpeedInv,
                            model.scoreSdSlope,
                            model.scoreSdIntercept,
                            model.marginRatingScale,
                            model.rdMax,
                            model.rdGrowthPerSqrtDay,
                            model.initialRdFraction,
                            model.minAverageScore,
                            model.scoreLearningWeightCap,
                            score.mode,
                            score.learningRate,
                            score.maxAdjustment,
                            score.minGames,
                            score.maxRatingPerGoal,
                            score.scaleByRd,
                            seasonality.mode,
                            seasonality.learningRate,
                            seasonality.maxAdjustment,
                            seasonality.halfLifeDays,
                            result.config.rebalanceRatings,
                            result.prediction.matches,
                            result.prediction.weightedMarginAccuracy,
                            result.prediction.wdlAccuracy,
                            result.prediction.brier,
                            result.prediction.logLoss,
                            result.prediction.marginMae,
                            result.prediction.teamScoreMae,
                            result.prediction.marginGaussianNll,
                            result.prediction.marginCoverage95,
                            result.prediction.exactScoreAccuracy,
                            result.scoreRating.averageAbsoluteAdjustment,
                            result.seasonality.averageAbsoluteOffset,
                            result.seasonality.monthOffsets.joinToString("|"),
                        ).joinToString(",")
                    )
                }
            }
        )
    }
}

data class RatingModelBenchmarkConfig(
    val model: RatingModelConfig,
    val scoreRating: ScoreRatingTweakConfig,
    val seasonality: ScoreSeasonalityConfig,
    val rebalanceRatings: Boolean = true,
) {
    override fun toString(): String =
        "$model score=${scoreRating.toString()} season=${seasonality.toString()} rebalance=$rebalanceRatings"
}

internal data class RatingModelBenchmarkResult(
    val config: RatingModelBenchmarkConfig,
    val prediction: PredictionBenchmarkSummary,
    val scoreRating: ScoreRatingTweakSummary,
    val seasonality: ScoreSeasonalitySummary,
    val byDate: Map<LocalDate, PredictionBenchmarkSummary>,
) {
    fun oneLine(): String =
        "${config.toString().padEnd(215)} ${prediction.compactLine()} | " +
            "scoreAdj=${scoreRating.averageAbsoluteAdjustment.formatMetric()} | ${seasonality.compactLine()}"
}
