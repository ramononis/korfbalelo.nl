package nl.korfbalelo.elo

import nl.korfbalelo.elo.application.RatingPipeline
import nl.korfbalelo.elo.domain.RatingRunWindow
import nl.korfbalelo.elo.infrastructure.FileEventCatalog
import java.io.File
import java.time.LocalDate
import kotlin.time.measureTime

// AI generated
object ScoreRatingBenchmark {
    private val eventCatalog = FileEventCatalog()

    @JvmStatic
    fun main(args: Array<String>) {
        val quick = "quick" in args
        val events = eventCatalog.loadAllEvents()
        val window = BenchmarkWindow.fromEvents(events)
        ApplicationNew.log = false

        val configs = configs(quick)
        println("Running ${configs.size} score-rating variants over ${window.first}..${window.second}")
        val results = mutableListOf<ScoreRatingBenchmarkResult>()
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

        println()
        println("Best weighted-margin accuracy:")
        byWeighted.take(12).forEachIndexed { index, result -> println("${index + 1}. ${result.oneLine()}") }
        println()
        println("Best Brier score:")
        byBrier.take(8).forEachIndexed { index, result -> println("${index + 1}. ${result.oneLine()}") }
        println()
        println("Best margin MAE:")
        byMargin.take(8).forEachIndexed { index, result -> println("${index + 1}. ${result.oneLine()}") }

        writeCsv(results)
        println()
        println("Finished in $elapsed")
    }

    private fun runVariant(
        config: ScoreRatingTweakConfig,
        window: Pair<LocalDate, LocalDate>,
        events: Set<RankingEvent>,
    ): ScoreRatingBenchmarkResult {
        ScoreRatingTweak.config = config
        ScoreSeasonality.config = ScoreSeasonalityConfig.off
        PredictionBenchmark.range = window
        AccuracyTracker.ignoreMatchesBelow = window.first
        RatingPipeline().run(
            events = events,
            window = RatingRunWindow(window.first, window.second),
            activeTeams = emptySet(),
            logEnabled = false,
            rebalanceRatings = true,
            onDate = {},
        )
        return ScoreRatingBenchmarkResult(
            config = config,
            prediction = PredictionBenchmark.accuracy.summary(),
            scoreRating = ScoreRatingTweak.summary(),
        )
    }

    private fun configs(quick: Boolean): List<ScoreRatingTweakConfig> {
        val learningRates = if (quick) {
            listOf(0.0025, 0.005, 0.01)
        } else {
            listOf(0.001, 0.0025, 0.005, 0.01, 0.02)
        }
        val caps = if (quick) listOf(5.0, 10.0) else listOf(2.5, 5.0, 10.0)
        val minGames = if (quick) listOf(8) else listOf(4, 8, 16)
        val scaleByRd = if (quick) listOf(false) else listOf(false, true)
        val modes = listOf(
            ScoreRatingTweakMode.MATCH_TOTAL_SCORE,
            ScoreRatingTweakMode.MATCH_TEAM_SCORE,
            ScoreRatingTweakMode.NEW_AVERAGE_SCORE,
            ScoreRatingTweakMode.HYBRID,
        )

        return listOf(ScoreRatingTweakConfig()) + modes.flatMap { mode ->
            learningRates.flatMap { learningRate ->
                caps.flatMap { maxAdjustment ->
                    minGames.flatMap { minGameCount ->
                        scaleByRd.map { rdScale ->
                            ScoreRatingTweakConfig(
                                mode = mode,
                                learningRate = learningRate,
                                maxAdjustment = maxAdjustment,
                                minGames = minGameCount,
                                scaleByRd = rdScale,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun writeCsv(results: List<ScoreRatingBenchmarkResult>) {
        val file = File("build/score-rating-benchmark.csv")
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine(
                    listOf(
                        "mode",
                        "learningRate",
                        "maxAdjustment",
                        "minGames",
                        "scaleByRd",
                        "matches",
                        "weightedMarginAccuracy",
                        "wdlAccuracy",
                        "brier",
                        "logLoss",
                        "marginMae",
                        "teamScoreMae",
                        "ratingAverageScoreCorrelation",
                        "snapshotCorrelation",
                        "scorePer100Rating",
                        "ratingPerGoal",
                        "averageAbsoluteAdjustment",
                        "cappedAdjustments",
                    ).joinToString(",")
                )
                results.forEach { result ->
                    appendLine(
                        listOf(
                            result.config.mode,
                            result.config.learningRate,
                            result.config.maxAdjustment,
                            result.config.minGames,
                            result.config.scaleByRd,
                            result.prediction.matches,
                            result.prediction.weightedMarginAccuracy,
                            result.prediction.wdlAccuracy,
                            result.prediction.brier,
                            result.prediction.logLoss,
                            result.prediction.marginMae,
                            result.prediction.teamScoreMae,
                            result.prediction.ratingAverageScoreCorrelation,
                            result.scoreRating.correlation,
                            result.scoreRating.scorePer100Rating,
                            result.scoreRating.ratingPerGoal,
                            result.scoreRating.averageAbsoluteAdjustment,
                            result.scoreRating.cappedAdjustments,
                        ).joinToString(",")
                    )
                }
            }
        )
    }
}

private data class ScoreRatingBenchmarkResult(
    val config: ScoreRatingTweakConfig,
    val prediction: PredictionBenchmarkSummary,
    val scoreRating: ScoreRatingTweakSummary,
) {
    fun oneLine(): String =
        "${config.toString().padEnd(72)} ${prediction.compactLine()} | ${scoreRating.compactLine()}"
}
