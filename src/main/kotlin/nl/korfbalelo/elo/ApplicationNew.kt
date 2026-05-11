package nl.korfbalelo.elo

import nl.korfbalelo.elo.RankingNew.ranking
import nl.korfbalelo.elo.SeasonPredicter.doOutdoor
import nl.korfbalelo.elo.SeasonPredicter.exportPoules
import nl.korfbalelo.elo.application.RatingPipeline
import nl.korfbalelo.elo.domain.RatingRunWindow
import nl.korfbalelo.elo.infrastructure.FileEventCatalog
import nl.korfbalelo.elo.infrastructure.FileRatingOutputs
import nl.korfbalelo.elo.startratings.StartRatingFinder.off
import nl.korfbalelo.mijnkorfbal.Scraper
import nl.korfbalelo.mijnkorfbal.Scraper.indoorPoules
import nl.korfbalelo.mijnkorfbal.Scraper.outdoorPoules
import java.io.File
import java.text.DecimalFormat
import java.time.LocalDate
import kotlin.jvm.JvmStatic
import kotlin.math.min

object ApplicationNew {
    var forceOutput = false
    var log = true
    val format = DecimalFormat("##.#####%")
    var startRatings = File("startratings.csv").readLines().filter(String::isNotBlank)
        .associate { it.split(",").let { (a, b) -> a to b.toDouble() } }.toMutableMap()

    val isolatedClusters = listOf(
        "1918-08-18 THOR (S)",
        "1921-11-20 Metallicus",
        "1922-05-07 KCK",
        "1923-10-28 WASV",
        "1925-03-22 Advendo (He)",
        "1930-01-19 OGKV",
        "1930-07-05 Kweek",
        "1936-01-12 Ornas",
        "1936-11-15 Oosterkwartier (S)",
        "1938-05-08 Duiven",
        "1941-10-05 Jong Leven",
        "1943-10-17 CSP",
        "1945-12-30 RKVVL",
        "1946-12-01 CKC",
        "1983-10-16 Blanckenburgh",
        "1990-12-09 Scharn",
    )

    private val eventCatalog = FileEventCatalog()
    private val ratingPipeline = RatingPipeline()
    private val outputFiles = FileRatingOutputs()

    init {
        for (s in isolatedClusters) {
            startRatings[s] = -300.0
        }
//        startRatings.clear()
    }

    fun startRating(spawnEvent: SpawnEvent) = startRatings[spawnEvent.toString()]
    val matches = mutableMapOf<Triple<LocalDate, String, String>, Match>()

    @JvmStatic
    fun main(args: Array<String>) {
        main(LocalDate.now(), LocalDate.now().minusDays(1)) {}
        Team.minDeltaBenchmark = min(Team.deltaBenchmark, Team.minDeltaBenchmark)
    }

    val matchesRemembered by lazy {
        eventCatalog.loadCurrentMatches()
    }

    fun matches() = matchesRemembered

    val events: Set<RankingEvent> by lazy {
        eventCatalog.loadAllEvents(matches()).also {
            println("Event count ${it.count()}")
        }
    }

    fun main(from: LocalDate, to: LocalDate, callback: (LocalDate) -> Unit) {
        matches.clear()
        val runResult = ratingPipeline.run(
            events = events,
            window = RatingRunWindow(from, to),
            activeTeams = Scraper.activeTeams,
            logEnabled = log,
            onDate = callback,
        )
        forceOutput = false

        if (log) {
            outputFiles.writePreSplitRanking(runResult.preSplitRanking)
            runResult.preSplitRanking.maxByOrNull { it.rating }.let(::printlnn)
            printlnn(runResult.preSplitRanking.size)
            ranking.values.minByOrNull { requireNotNull(it.lastDate, it::toString) }.let(::printlnn)
            ranking.values.maxByOrNull { it.rating }.let(::printlnn)
            outputFiles.writeWebRanking(ranking.values)
            outputFiles.writeDiscontinuedTeams(DiscontinuedTeams.all())
            ranking["Olympia '22"].let(::printlnn)
            outputFiles.writeOrigins(originMap)
            printlnn(ranking.size)
            printlnn(ranking.filterValues { it.lastDate!!.isAfter(LocalDate.of(2020, 9, 1)) }.size)
            outputFiles.writeGraph(RankingNew.graph)
        }

        if (maxPredicability < Team.benchmark2Field) {
            maxPredicability = Team.benchmark2Field
            println("\nPredictability benchmark: ${maxPredicability - record} - ${format.format(maxPredicability.toDouble() / Team.totalDiff)}")
        }
        printlnn("Delta benchmark: ${off - Team.deltaBenchmark}")
        if (log) {
            val maxId = outputFiles.writeAggregates(events)
            if (maxPredicability2 < Team.benchmark3Field) {
                maxPredicability2 = Team.benchmark3Field
                println("\nPredictability benchmark2: ${maxPredicability2 + record2}")
            }
            val hasScrapedPoules = indoorPoules.isNotEmpty() || outdoorPoules.isNotEmpty()
            if (hasScrapedPoules) {
                if (doOutdoor) {
                    exportPoules<SeasonPredicter.OutdoorSeason>()
                } else {
                    exportPoules<SeasonPredicter.IndoorSeason>()
                }
            } else {
                printlnn("Skipping poule export: no scraped poules loaded")
            }
            SeasonContext.writeFrontendSeasonModule()
            val primaryActiveSeason = SeasonContext.primaryActive
            val activeSeasonNames = SeasonContext.active.map { it.seasonName }
            File("web/public/meta.json").writeText(gson.toJson(mapOf(
                "H" to Team.H,
                "SD_A" to Team.SD_A,
                "SD_B" to Team.SD_B,
                "RD_PERIOD_DAYS" to Team.RD_PERIOD_DAYS,
                "RD_MAX" to Team.RD_MAX,
                "MAX_ID" to maxId,
                "ACTIVE_SEASON" to primaryActiveSeason.seasonName,
                "ACTIVE_MODE" to primaryActiveSeason.mode.name.lowercase(),
                "ACTIVE_SEASONS" to activeSeasonNames,
                "INDOOR_SEASON" to SeasonContext.indoor.seasonName,
                "OUTDOOR_SEASON" to SeasonContext.outdoor.seasonName,
            )))
            println("This season correct predictions: ${Team.nCorrectBM} / ${Team.nMatchesBM} = ${(Team.nCorrectBM.toDouble() / Team.nMatchesBM) * 100.0}%")
            println("This season perfect predictions: ${Team.nVeryCorrectBM} / ${Team.nAllMatchesBM} = ${(Team.nVeryCorrectBM.toDouble() / Team.nAllMatchesBM) * 100.0}%")
        }
        Thread.yield()
    }

    const val record = 427497
    var maxPredicability = -1000000000
    const val record2 = 661548.2851552791
    var maxPredicability2 = -1000000000.0

    fun printlnn(string: Any?) {
        if (log) println(string)
    }
}
