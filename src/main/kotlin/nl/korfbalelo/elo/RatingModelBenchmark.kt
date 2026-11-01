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
        val quick = "quick" in args
        val events = eventCatalog.loadAllEvents()
        val window = BenchmarkWindow.fromEvents(events)
        ApplicationNew.log = false

        // AI generated: an honest sensitivity check excludes the outcome-fitted spawn priors.
        if (System.getProperty("elo.benchmark.fittedStarts", "true") == "false") {
            ApplicationNew.startRatings.clear()
        }
        val configs = when {
            "refine" in args -> refinementConfigs()
            "finalists" in args -> finalistConfigs()
            "research" in args -> researchConfigs()
            else -> configs(quick)
        }
        println("Running ${configs.size} rating-model variants over ${window.first}..${window.second}")
        val results = mutableListOf<RatingModelBenchmarkResult>()
        val elapsed = measureTime {
            configs.forEachIndexed { index, config ->
                val result = runVariant(config, window, events)
                results += result
                println("${index + 1}/${configs.size} ${result.oneLine()}")
            }
        }

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

        writeCsv(results)
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

    // AI generated: interaction search restricted to development data.
    private fun refinementConfigs(): List<RatingModelBenchmarkConfig> {
        val base = researchConfigs().first()
        return listOf(base) + listOf(280.0, 320.0, 360.0).flatMap { scale ->
            listOf(0.12 to 1.5, 0.166 to 1.85, 0.22 to 2.2).flatMap { (a, b) ->
                listOf(0.5, 1.0).map { period ->
                    base.copy(model = base.model.copy(marginRatingScale = scale,
                        scoreSdSlope = a, scoreSdIntercept = b, rdPeriodDays = period,
                        scoreLearningWeightCap = 4.0))
                }
            }
        }
    }

    // AI generated: frozen after the 2000-2022 search, before evaluating later seasons.
    private fun finalistConfigs(): List<RatingModelBenchmarkConfig> {
        val base = researchConfigs().first()
        return listOf(
            base,
            base.copy(model = base.model.copy(scoreLearningWeightCap = 4.0)),
            base.copy(model = base.model.copy(marginRatingScale = 360.0, scoreLearningWeightCap = 4.0)),
            base.copy(model = base.model.copy(marginRatingScale = 320.0, scoreSdSlope = 0.12,
                scoreSdIntercept = 1.5, scoreLearningWeightCap = 4.0)),
        )
    }

    private fun researchConfigs(): List<RatingModelBenchmarkConfig> {
        val base = RatingModelBenchmarkConfig(
            // AI generated: preserve the pre-investigation reference after changing production defaults.
            RatingModelConfig.standard.copy(marginRatingScale = 400.0, scoreLearningWeightCap = Double.POSITIVE_INFINITY),
            ScoreRatingTweakConfig.fromSystemProperties(),
            ScoreSeasonalityConfig.fromSystemProperties(),
        )
        val m = base.model
        return (listOf(base) +
            listOf(0.5, 2.0, 4.0, 7.0, 14.0).map { base.copy(model = m.copy(rdPeriodDays = it)) } +
            listOf(280.0, 320.0, 360.0, 440.0, 500.0).map { base.copy(model = m.copy(marginRatingScale = it)) } +
            listOf(60.0, 130.0, 170.0).map { base.copy(model = m.copy(scoreSpeedInv = it)) } +
            listOf(4.0, 8.0, 16.0, 32.0).map { base.copy(model = m.copy(scoreLearningWeightCap = it)) } +
            listOf(0.12 to 1.5, 0.22 to 2.2, 0.24 to 2.6, 0.3 to 2.2).map { (a, b) ->
                base.copy(model = m.copy(scoreSdSlope = a, scoreSdIntercept = b))
            } +
            base.copy(scoreRating = ScoreRatingTweakConfig()) +
            base.copy(seasonality = ScoreSeasonalityConfig.off) +
            listOf(0.0, 1.0 / 16.0, 1.0 / 4.0).map { base.copy(model = m.copy(homeAdvantageSpeed = it)) }
        ).distinct()
    }

    private fun configs(quick: Boolean): List<RatingModelBenchmarkConfig> {
        val standard = RatingModelConfig.standard
        val defaultScoreRating = ScoreRatingTweakConfig.fromSystemProperties()
        val defaultSeasonality = ScoreSeasonalityConfig.fromSystemProperties()
        val baseline = RatingModelBenchmarkConfig(standard, defaultScoreRating, defaultSeasonality)
        val noSeasonalityBaseline = baseline.copy(seasonality = ScoreSeasonalityConfig.off)

        val homeInitials = if (quick) listOf(25.0, 30.0, 35.0, 40.0, 45.0) else listOf(20.0, 25.0, 30.0, 35.0, 40.0, 45.0, 50.0)
        val homeSpeeds = if (quick) listOf(0.0, 1.0 / 128.0, 1.0 / 64.0, 1.0 / 32.0) else listOf(0.0, 1.0 / 256.0, 1.0 / 128.0, 1.0 / 64.0, 1.0 / 48.0, 1.0 / 32.0)
        val scoreSpeedInvs = if (quick) listOf(60.0, 80.0, 100.0, 130.0, 170.0) else listOf(40.0, 60.0, 80.0, 100.0, 130.0, 170.0, 220.0)
        val marginScales = if (quick) listOf(320.0, 360.0, 400.0, 440.0, 500.0) else listOf(280.0, 320.0, 360.0, 400.0, 440.0, 500.0, 580.0)
        val sdSlopes = if (quick) listOf(0.12, 0.166, 0.22) else listOf(0.08, 0.12, 0.166, 0.20, 0.24, 0.30)
        val sdIntercepts = if (quick) listOf(1.5, 1.85, 2.2) else listOf(1.2, 1.5, 1.85, 2.2, 2.6)
        val rdMaxes = if (quick) listOf(300.0, 350.0, 450.0) else listOf(250.0, 300.0, 350.0, 450.0, 600.0)
        val rdPeriods = if (quick) listOf(0.5, 1.0, 2.0, 7.0) else listOf(0.25, 0.5, 1.0, 2.0, 7.0, 14.0)
        val minScores = if (quick) listOf(0.5, 1.0) else listOf(0.1, 0.5, 1.0, 2.0)

        val home = homeInitials.flatMap { initial ->
            homeSpeeds.map { speed ->
                baseline.copy(
                    model = standard.copy(
                        initialHomeAdvantage = initial,
                        homeAdvantageSpeed = speed,
                    )
                )
            }
        }
        val scoreSpeed = scoreSpeedInvs.map { scoreSpeedInv ->
            baseline.copy(model = standard.copy(scoreSpeedInv = scoreSpeedInv))
        }
        val margin = marginScales.flatMap { marginScale ->
            sdSlopes.flatMap { slope ->
                sdIntercepts.map { intercept ->
                    baseline.copy(
                        model = standard.copy(
                            marginRatingScale = marginScale,
                            scoreSdSlope = slope,
                            scoreSdIntercept = intercept,
                        )
                    )
                }
            }
        }
        val rd = rdMaxes.flatMap { rdMax ->
            rdPeriods.map { rdPeriodDays ->
                baseline.copy(
                    model = standard.copy(
                        rdMax = rdMax,
                        rdPeriodDays = rdPeriodDays,
                    )
                )
            }
        }
        val scoreFloor = minScores.map { minAverageScore ->
            baseline.copy(model = standard.copy(minAverageScore = minAverageScore))
        }
        val scoreRatingVariants = listOf(
            baseline.copy(scoreRating = ScoreRatingTweakConfig(mode = ScoreRatingTweakMode.OFF)),
            baseline.copy(scoreRating = defaultScoreRating.copy(learningRate = 0.0005)),
            baseline.copy(scoreRating = defaultScoreRating.copy(learningRate = 0.002)),
            baseline.copy(scoreRating = defaultScoreRating.copy(maxAdjustment = 5.0)),
            baseline.copy(scoreRating = defaultScoreRating.copy(maxAdjustment = 20.0)),
            baseline.copy(scoreRating = defaultScoreRating.copy(scaleByRd = true)),
        )
        val rebalance = listOf(
            baseline.copy(rebalanceRatings = false),
        )
        val seasonalityConfigs = seasonalityConfigs(quick)
        val promisingModels = listOf(
            standard,
            standard.copy(scoreSdSlope = 0.166, scoreSdIntercept = 1.85, marginRatingScale = 320.0),
            standard.copy(scoreSdSlope = 0.24, scoreSdIntercept = 2.6, marginRatingScale = 400.0),
            standard.copy(scoreSdSlope = 0.3, scoreSdIntercept = 2.2, marginRatingScale = 320.0),
        ).distinct()
        val seasonality = promisingModels.flatMap { model ->
            seasonalityConfigs.map { seasonality ->
                baseline.copy(model = model, seasonality = seasonality)
            }
        }
        val noSeasonality = promisingModels.map { model ->
            baseline.copy(model = model, seasonality = ScoreSeasonalityConfig.off)
        }

        return (
            listOf(baseline) +
                noSeasonalityBaseline +
                home +
                scoreSpeed +
                margin +
                rd +
                scoreFloor +
                scoreRatingVariants +
                rebalance +
                noSeasonality +
                seasonality
            )
            .distinct()
    }

    private fun seasonalityConfigs(quick: Boolean): List<ScoreSeasonalityConfig> {
        val learningRates = if (quick) {
            listOf(0.0025, 0.005, 0.01, 0.02)
        } else {
            listOf(0.001, 0.0025, 0.005, 0.01, 0.02, 0.04)
        }
        val caps = if (quick) listOf(3.0, 6.0) else listOf(2.0, 3.0, 6.0, 10.0)
        val halfLives = if (quick) {
            listOf(0.0, 3650.0)
        } else {
            listOf(0.0, 1825.0, 3650.0, 10950.0)
        }
        return learningRates.flatMap { learningRate ->
            caps.flatMap { cap ->
                halfLives.map { halfLife ->
                    ScoreSeasonalityConfig(
                        mode = ScoreSeasonalityMode.MONTHLY_OFFSET,
                        learningRate = learningRate,
                        maxAdjustment = cap,
                        halfLifeDays = halfLife,
                    )
                }
            }
        }
    }

    private fun writeCsv(results: List<RatingModelBenchmarkResult>) {
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
                        "initialHomeAdvantage",
                        "homeAdvantageSpeed",
                        "scoreSpeedInv",
                        "scoreSdSlope",
                        "scoreSdIntercept",
                        "marginRatingScale",
                        "rdMax",
                        "rdPeriodDays",
                        "minAverageScore",
                        "scoreLearningWeightCap",
                        "scoreRatingMode",
                        "scoreRatingLearningRate",
                        "scoreRatingMaxAdjustment",
                        "scoreRatingMinGames",
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
                            model.initialHomeAdvantage,
                            model.homeAdvantageSpeed,
                            model.scoreSpeedInv,
                            model.scoreSdSlope,
                            model.scoreSdIntercept,
                            model.marginRatingScale,
                            model.rdMax,
                            model.rdPeriodDays,
                            model.minAverageScore,
                            model.scoreLearningWeightCap,
                            score.mode,
                            score.learningRate,
                            score.maxAdjustment,
                            score.minGames,
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
