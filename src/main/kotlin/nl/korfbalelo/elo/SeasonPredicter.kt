package nl.korfbalelo.elo

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import nl.korfbalelo.elo.ApplicationNew.forceOutput
import nl.korfbalelo.elo.ApplicationNew.matches
import nl.korfbalelo.elo.PoulePredicter.Companion.pointsTie
import nl.korfbalelo.elo.PoulePredicter.Companion.reset
import nl.korfbalelo.elo.RankingNew.ranking
import nl.korfbalelo.elo.application.DeclarativeSeasonTransitionSimulator
import nl.korfbalelo.mijnkorfbal.PouleData
import nl.korfbalelo.mijnkorfbal.Scraper.scrapePoules
import nl.korfbalelo.mijnkorfbal.Scraper.indoorPoules
import nl.korfbalelo.mijnkorfbal.Scraper.outdoorPoules
import nl.korfbalelo.mijnkorfbal.Scraper.specialMatches
import nl.korfbalelo.mijnkorfbal.StaticPoules
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.SplittableRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.random.RandomGenerator
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.pow
import java.time.LocalDate.of as ld

object SeasonPredicter {
    internal val klChampionshipPoolPatterns = listOf(
        Regex("Play-off Kenonz KL", RegexOption.IGNORE_CASE),
        Regex("KL play-off", RegexOption.IGNORE_CASE),
        Regex("KL(\\(\\d+\\))? Eindfase", RegexOption.IGNORE_CASE),
    )
    internal val klPromotionPoolPatterns = listOf(
        Regex("Play-off KL2", RegexOption.IGNORE_CASE),
        Regex("KL/KL2 play-down", RegexOption.IGNORE_CASE),
    )

    // included: before
    val events = ConcurrentHashMap<Pair<String, String>, AtomicInteger>()
    private val deterministicMode = System.getProperty("elo.predict.deterministic", "false").toBoolean()
    private val configuredConcurrency = System.getProperty("elo.predict.concurrent")
    private val configuredIterations = System.getProperty("elo.predict.iterations")
    private val baseSeed = System.getProperty("elo.predict.seed", "20260101").toLong()
    private val configuredEndDate = System.getProperty("elo.predict.endDate")?.let(LocalDate::parse)
    private val forceInitialOutput = System.getProperty("elo.predict.forceInitialOutput", "false").toBoolean()
    private val clearSnapshotCsvsOnStart = System.getProperty("elo.predict.clearSnapshots", "false").toBoolean()
    private val excludedSnapshotPoules = System.getProperty("elo.predict.excludedPoules", "")
        .split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
    private val indoorTransitionSimulator by lazy {
        DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/zaal2627__zaal2728.json"))
    }
    private val outdoorTransitionSimulator by lazy {
        DeclarativeSeasonTransitionSimulator.fromFile(File("rules/pd/veld2526vj__veld2627nj.json"))
    }

    val concurrency = configuredConcurrency?.toInt()
        ?: if (deterministicMode) 1
        else Runtime.getRuntime().availableProcessors()

    //    var betterThan = ConcurrentHashMap<String, Int>()
//    var lesserThan = ConcurrentHashMap<String, Int>()
    val n = configuredIterations?.toInt() ?: 100_000

    fun event(team: Team, event: String) {
        if ("champion" !in event && "rank" !in event && team.name in removedTeams) {
            error("removed! $team")
        }
        events.getOrPut(team.name to event) { AtomicInteger(0) }.incrementAndGet()
    }

    val concurrent = concurrency > 1
    val count = AtomicInteger(0)
    var doOutdoor = false

    val path = "web/public/csv/"

    internal fun requestedStartDate(args: Array<String>): LocalDate? =
        args.firstOrNull()?.let(LocalDate::parse)

    @JvmStatic
    fun main(args: Array<String>) {
        val requestedStartDate = requestedStartDate(args)
        val predictionEndDate = configuredEndDate ?: LocalDate.now()
        if (args.contains("zaal")) {
            doOutdoor = false
        } else if (args.contains("veld")) {
            val shouldForceFullOutdoorOutput = System.getProperty(
                "elo.predict.forceFullOutdoorOutput",
                LocalDate.now().isBefore(LocalDate.of(2026, 3, 27)).toString(),
            ).toBoolean()
            if (shouldForceFullOutdoorOutput) {
                File("$path/${SeasonContext.outdoor.seasonName}").let {
                    if (it.exists()) {
                        it.deleteRecursively()
                    }
                }
            }
            doOutdoor = true
            scrapePoules()
            clearSnapshotCsvs(SeasonContext.outdoor.seasonName, outdoorPoules.keys)

            requestedStartDate?.let {
                forceOutput = shouldForceFullOutdoorOutput
                runApplicationWithPredictionFallback(it, predictionEndDate)
            } ?: run {
                ApplicationNew.main(arrayOf())
                forceOutput = shouldForceFullOutdoorOutput
                run(predictionEndDate)
            }
            return
        }
        scrapePoules()
        clearSnapshotCsvs(SeasonContext.indoor.seasonName, indoorPoules.keys)
        requestedStartDate?.let {
            forceOutput = forceInitialOutput
            runApplicationWithPredictionFallback(it, predictionEndDate)
        } ?: run {
            ApplicationNew.main(arrayOf())
            run(predictionEndDate)
        }
    }

    private fun runApplicationWithPredictionFallback(startDate: LocalDate, endDate: LocalDate) {
        val shouldForceOutput = forceOutput
        var didRunPrediction = false
        ApplicationNew.main(startDate, endDate) {
            didRunPrediction = true
            run(it)
        }
        if (!didRunPrediction && !startDate.isAfter(endDate)) {
            forceOutput = shouldForceOutput
            run(startDate)
        }
    }

    private fun clearSnapshotCsvs(seasonName: String, pouleNames: Set<String>) {
        if (!clearSnapshotCsvsOnStart) {
            return
        }
        val clearablePoules = pouleNames - excludedSnapshotPoules
        File("$path/$seasonName")
            .listFiles { file ->
                file.isFile && file.extension == "csv" && file.nameWithoutExtension in clearablePoules
            }
            .orEmpty()
            .forEach(File::delete)
    }

    //    val outdoorTiers = listOf("ek", "ek2", "hk", "ok", "1k", "2k", "3k", "4k")
    val outdoorTiers = listOf("ek", "ek", "hk", "ok", "1k", "2k", "3k", "4k")
    val indoorTiers = listOf("kl", "kl2", "hk", "ok", "1k", "2k", "3k")
    val removedTeams = if (doOutdoor) {
        setOf<String>(
        )
    } else {
        setOf<String>(
        )
    }

    var zeDate: LocalDate? = null
    internal fun hasSnapshotWork(date: LocalDate): Boolean =
        if (doOutdoor) {
            OutdoorSeason().predicters.any { shouldWritePouleSnapshot(it, date) }
        } else {
            IndoorSeason().predicters.any { shouldWritePouleSnapshot(it, date) }
        }

    fun run(date: LocalDate? = null) {
        // DATE IS INCLUSIVE
        date?.let { zeDate = it }
        if (date != null && !forceOutput && !hasSnapshotWork(date)) {
            return
        }
        count.set(0)
        events.clear()
        if (doOutdoor) {
            simulate<OutdoorSeason>(date)

            removedTeams.forEach { t ->
                events.keys().toList().forEach { (t2, e) ->
                    if (t == t2 && e != "champion" && "rank" !in e) error("WTF $t $t2 $e")
                }
            }
//            checkCount("ek", 4 * 4)
//            checkCount("ek2", 4 * 4)
            checkCount("ek", 8 * 4)
            checkCount("hk", 8 * 4)
            checkCount("ok", 8 * 4)
            checkCount("1k", 12 * 4)
            checkCount("2k", 12 * 4)
            checkCount("3k", 12 * 4)
            checkCount("4k", 36)
            buildString {
                ranking.values.sortedByDescending { it.rating }.forEach { t ->
                    append("\"${t.name}\",\"${t.rating}\",")
                    outdoorTiers.joinToString(",") {
                        "\"${(events[t.name to it]?.get() ?: 0) / count.toDouble()}\""
                    }.let(::appendLine).toString().let { File("tiers.csv").writeText(it) }
                }
            }
        } else {
            simulate<IndoorSeason>(date)

            checkCount("kl", 10)
            checkCount("kl2", 10)
            checkCount("hk", 2 * 8)
            checkCount("ok", 4 * 8)
            checkCount("1k", 8 * 8)
            checkCount("2k", 8 * 8)
            checkCount("3k", 80)
            buildString {
                ranking.values.sortedByDescending { it.rating }.forEach { t ->
                    append("\"${t.name}\",\"${t.rating}\",")
                    indoorTiers.joinToString(",") {
                        "\"${(events[t.name to it]?.get() ?: 0) / count.toDouble()}\""
                    }.let(::appendLine).toString().let { File("tiers.csv").writeText(it) }
                }
            }
        }


        buildString {
            listOf(
                pointsTie,
                PoulePredicter.subPointsTie,
                PoulePredicter.subBalanceTie,
                PoulePredicter.subScoredTie,
                PoulePredicter.balanceTie,
                PoulePredicter.scoredTie
            ).forEach {
                val lol = it.drop(2).reversed().dropWhile { it == 0 }.reversed()
                lol.forEach { append("${it.toDouble() / n * 100}%\t") }
                append("xxxx\t")
            }
        }
        reset()
        forceOutput = false
    }

    private fun checkCount(comp: String, c: Int) {
        val n = events.entries.filter { it.key.second == comp }.sumOf { it.value.get() }
        if (n % count.get() != 0) {
            error("$comp: $n % $count != 0")
        }
        if (n / count.get() != c) {
            error("$comp: $n / $count != $c")
        }
    }

    context(random: RandomGenerator)
    private fun applyTransitionRules(
        simulator: DeclarativeSeasonTransitionSimulator,
        predicters: List<PoulePredicter>,
    ) {
        val predictersByName = predicters.associateBy(PoulePredicter::pouleName)
        val transitionResult = simulator.simulatePredicters(predictersByName, random)
        val sourceTierByTeam = simulator.sourceTierByTeam(predictersByName)
        val sourceStandingByTeam = predicters.flatMap { predicter ->
            predicter.executor.result.mapIndexed { index, (team) ->
                team.name to (predicter.pouleName to index + 1)
            }
        }.toMap()
        val teamsByName = predicters.flatMap { it.teams.asIterable() }.associateBy(Team::name)

        transitionResult.tierByTeam.forEach { (teamName, targetTier) ->
            val team = teamsByName.getValue(teamName)
            val sourceTier = sourceTierByTeam.getValue(teamName)
            val (sourcePouleName, sourcePosition) = sourceStandingByTeam.getValue(teamName)
            event(team, targetTier)
            if (simulator.seasonOutcomePromote(sourceTier, sourcePouleName, sourcePosition, targetTier)) {
                event(team, "promote")
            }
            if (simulator.seasonOutcomeRelegate(sourceTier, targetTier)) {
                event(team, "relegate")
            }
        }

        transitionResult.eventsByTeam.forEach { (teamName, extraEvents) ->
            val team = teamsByName.getValue(teamName)
            extraEvents.forEach { event(team, it) }
        }
    }


    private fun filterPoules(
        poules: Map<String, PouleData>,
        regex: Regex,
        size: Int,
        specialPoules: Set<String> = emptySet(),
        extraSpecialMatches: List<Match> = emptyList(),
    ) =
        poules.filter { (k) -> regex.matchEntire(k.uppercase()) != null }.entries.sortedBy { it.key }.map {
            val extraMatches = (specialPoules.flatMap { poules[it]?.second ?: emptyList() } + extraSpecialMatches)
                .distinctBy(Match::formatFixture)
            PoulePredicter(
                it.key, it.value.first, (it.value.second + extraMatches).distinctBy(Match::formatFixture), zeDate
            )
        }.also {
            it.size == size ||
                error(regex)
        }

    abstract class Season {
        val predicters = mutableListOf<PoulePredicter>()

        context(_: RandomGenerator)
        fun doSimulate(date: LocalDate?) {
            predicters.forEach {
                it.simulate()
                it.executor.result.first().let { (t) ->
                    if (it.pouleName != "KL"
                        && !it.pouleName.startsWith("EK-0") // ONLY FOR VOORJAAR
                        ) {
                        event(t, "champion")
                    }
                }
                it.executor.result.forEachIndexed { i, (t) ->
                    event(t, "rank$i")
                }
            }
            seasonSpecificStuff(date)
        }

        context(_: RandomGenerator)
        abstract fun seasonSpecificStuff(date: LocalDate?)

        abstract val name: String
    }

    class IndoorSeason : Season() {
        private fun findSpecialIndoorPoules(vararg patterns: Regex): Set<String> =
            indoorPoules.keys.filter { name -> patterns.any { it.matches(name) } }.toSet()

        fun filterIndoorPoules(
            regex: Regex,
            assertSize: Int,
            specialPoules: Set<String> = emptySet(),
            extraSpecialMatches: List<Match> = emptyList(),
        ) =
            filterPoules(indoorPoules, regex, assertSize, specialPoules, extraSpecialMatches).also {
                predicters.addAll(it)
            }

        override val name = SeasonContext.indoor.seasonName
        private val klChampionshipPoules = findSpecialIndoorPoules(*klChampionshipPoolPatterns.toTypedArray())
        private val klPromotionPoules = findSpecialIndoorPoules(*klPromotionPoolPatterns.toTypedArray())
        private val klTeams = indoorPoules.getValue("KL").first.keys
        private val kl2Teams = indoorPoules.getValue("KL2").first.keys
        private val klRelatedSpecialMatches = specialMatches.values
            .filter { match ->
                match.home in klTeams
                    || match.away in klTeams
                    || match.home in kl2Teams
                    || match.away in kl2Teams
            }
        private val kl2RelatedSpecialMatches = specialMatches.values
            .filter { match ->
                match.home in kl2Teams || match.away in kl2Teams
            }
        val korfbalLeague = filterIndoorPoules(
            Regex("KL"),
            1,
            klChampionshipPoules + klPromotionPoules,
            klRelatedSpecialMatches,
        ).first()
        val korfbalLeague2 = filterIndoorPoules(
            Regex("KL2"),
            1,
            klPromotionPoules,
            kl2RelatedSpecialMatches,
        ).first()

        context(_: RandomGenerator)
        override fun seasonSpecificStuff(date: LocalDate?) {
            korfbalLeague.executor.result.take(4).map { it.first }.forEach {
                event(it, "promote")
            }
            applyTransitionRules(indoorTransitionSimulator, predicters)
        }
    }

    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    class OutdoorSeason : Season() {
        override val name = SeasonContext.outdoor.seasonName

        context(_: RandomGenerator)
        override fun seasonSpecificStuff(date: LocalDate?) {
            applyTransitionRules(outdoorTransitionSimulator, predicters)
        }
    }

    var didHeader = false

    private fun shouldForceSeedPouleLine(predicter: PoulePredicter, date: LocalDate): Boolean {
        if (doOutdoor) {
            return false
        }
        if (!predicter.pouleName.startsWith("3")) {
            return false
        }
        val firstMatchDate = predicter.matches
            .filterNot(Match::special)
            .minOfOrNull(Match::date)
            ?: return false
        return date == firstMatchDate.minusDays(7)
    }

    internal fun shouldWritePouleSnapshot(predicter: PoulePredicter, date: LocalDate): Boolean =
        predicter.pouleName !in excludedSnapshotPoules &&
            (forceOutput
                || shouldForceSeedPouleLine(predicter, date)
                || predicter.teams.any { it.lastDate == date }
                || predicter.matches.any { it.special && it.date == date })

    @OptIn(ExperimentalAtomicApi::class)
    private inline fun <reified T : Season> simulate(date: LocalDate?) {
        val construct = { T::class.java.getConstructor().newInstance() }
        val lastPercentage = AtomicInt(0)
        if (concurrent) {
            runBlocking {

                val threads = (1..concurrency).toList().map { workerId ->
                    Thread {
                        with(SplittableRandom(baseSeed + workerId)) {
                            val season = construct()
                            while (count.incrementAndGet() <= n) {
                                val percentage = count.get() * 100 / n
                                season.doSimulate(date)
                                if (lastPercentage.compareAndSet(percentage - 1, percentage)) {
                                    println("Progress: $percentage")
                                }

                            }
                        }
                    }.also(Thread::start)
                }
//                Thread.sleep(20000)
//                threads.forEach { it.interrupt() }
                threads.forEach { it.join() }
                count.addAndGet(-concurrency)
                check(count.get() == n) { "$count != $n" }
//                (betterThan.keys.toSet() + lesserThan.keys.toSet()).sortedBy { betterThan[it] }.forEach {
//                    println("$it,${betterThan[it]},${lesserThan[it]}")
//                }
            }
        } else {
            val season = construct()
            with(SplittableRandom(baseSeed)) {
                repeat(n) {
                    season.doSimulate(date)
                    count.incrementAndGet()
                }
            }
        }
        if (date != null) {
            val season = construct()
            for (predicter in season.predicters) {
                if (!shouldWritePouleSnapshot(predicter, date)) {
                    continue
                }
                val file = File("$path/${season.name}/${predicter.pouleName}.csv").also { it.parentFile.mkdirs() }
                val didHeader = file.takeIf(File::exists)?.readText()?.startsWith("date") == true
                val dateString = format.format(date)
                // remove line starting with date
                file.takeIf { it.exists() }
                    ?.readLines()
                    ?.filter { !it.startsWith(dateString) }
                    ?.joinToString("\n")
                    ?.let(file::writeText)
                val fileEndsWithNewline = file.takeIf(File::exists)?.readText()?.endsWith("\n") == true
                file.appendText(buildString {
                    if (!didHeader) {
                        append("date\tpouleData\t")
                        appendLine(predicter.teamNames.flatMap { listOf(it, it, it, it, it) }.joinToString("\t"))
                    }
                    if (!fileEndsWithNewline) append("\n")
                    append("${format.format(date)}\t")
                    append("${gson.toJson(predicter.currentStanding())}\t")
                    appendLine(predicter.teamNames.flatMap { t ->
                        val champion = events[t to "champion"]?.toDouble() ?: 0.0
                        val plus = events[t to "promote"]?.toDouble() ?: 0.0
                        val minus = events[t to "relegate"]?.toDouble() ?: 0.0
                        listOf(
                            champion / count.toDouble(),
                            plus / count.toDouble(),
                            minus / count.toDouble(),
                            ranking[t]!!.rating,
                            ranking[t]!!.rd
                        )
                    }.joinToString("\t"))
                })
                sortPouleSnapshotFile(file)
            }
            didHeader = true
        }
//        construct().predicters.sortedBy { it.teamNames.maxOf { events[it to "rank0"]!!.get() } }
//            .forEach { println(it.pouleName to it.teamNames.maxOf { events[it to "rank0"]!!.get().toDouble() / count.get() }) }

        val statToCheck = "luck"
        events.entries.filter { it.key.second == statToCheck }.sortedByDescending { it.value.get() }.forEach {
            println(it.key.first + "\t" + (it.value.toDouble() / count.get()))
        }
    }

    private fun sortPouleSnapshotFile(file: File) {
        val lines = file.readLines().filter(String::isNotBlank)
        if (lines.isEmpty()) {
            return
        }
        file.writeText(
            (listOf(lines.first()) + lines.drop(1).sortedBy { it.substringBefore('\t') })
                .joinToString("\n", postfix = "\n")
        )
    }

    fun match(t1: Team, t2: Team, d: LocalDate) = matches[Triple(d, t1.name, t2.name)]

    fun Match.winner() = when {
        homeScore > awayScore -> home
        awayScore > homeScore -> away
        else -> null
    }

    fun Match.loser() = when {
        homeScore < awayScore -> home
        awayScore < homeScore -> away
        else -> null
    }

    inline fun <reified T : Season> exportPoules() {
        val season = T::class.java.getConstructor().newInstance()
        val useStablePouleOrder = StaticPoules.hasIndoorPoules(season.name)
        File("web/public/${season.name}.json")
            .writeText(
                Gson().toJson(
                    season.predicters
                        .map {
                            it.pouleName to if (useStablePouleOrder) {
                                it.teamNames
                            } else {
                                it.teamNames.sortedByDescending { teamName -> ranking[teamName]!!.rating }
                            }
                        }
                        .groupBy {
                            when (it.first) {
                                in Regex("KL") -> "Korfbal League"
                                in Regex("KL2") -> "Korfbal League"
                                in Regex("EK-D.*") -> "Ereklasse D"
                                in Regex("EK.*") -> "Ereklasse"
                                in Regex("HK.*") -> "Hoofdklasse"
                                in Regex("OK.*") -> "Overgangsklasse"
                                in Regex("1.*") -> "1e Klasse"
                                in Regex("2.*") -> "2e Klasse"
                                in Regex("3.*") -> "3e Klasse"
                                in Regex("4.*") -> "4e Klasse"
                                else -> error("WTF")
                            }
                        }.mapValues { it.value.toMap() }
                )
            )
    }
}
