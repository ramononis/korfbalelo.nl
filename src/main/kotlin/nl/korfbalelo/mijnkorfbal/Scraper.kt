package nl.korfbalelo.mijnkorfbal

import nl.korfbalelo.elo.Match
import nl.korfbalelo.elo.RankingNew
import nl.korfbalelo.elo.SeasonContext
import nl.korfbalelo.elo.gson
import nl.korfbalelo.elo.pmap
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

typealias PouleData = Pair<Map<String, Int>, List<Match>>

object Scraper {
    var forceNetwork = System.getProperty("elo.scraper.forceNetwork", "false").toBoolean()
    private val writeCurrentMatches = System.getProperty("elo.scraper.writeCurrentMatches", "true").toBoolean()
    val outdoorPoules = ConcurrentHashMap<String, PouleData>()
    val indoorPoules = ConcurrentHashMap<String, PouleData>()
    val specialMatches = ConcurrentHashMap<String, Match>()

    internal fun isPostSeasonPool(poolName: String): Boolean =
        listOf("play", "eindfase").any { marker ->
            poolName.contains(marker, ignoreCase = true)
        }

    // AI generated: pool scraping updates active teams concurrently.
    val activeTeams: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @JvmStatic
    fun main(args: Array<String>) {
        val matches = scrapePoules()
        println("Scraped ${matches.size} first-team results; latest date: ${matches.maxOfOrNull { it.date }}")
    }

    fun scrapePoules(): List<Match> {
        // AI generated: stage a complete scrape before publishing state or generated files.
        val api = CompetitionApi(forceNetwork = forceNetwork)
        val nextOutdoor = ConcurrentHashMap<String, PouleData>()
        val nextIndoor = ConcurrentHashMap<String, PouleData>()
        val nextSpecial = ConcurrentHashMap<String, Match>()
        val nextActive: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val hasStaticIndoorPoules = System.getProperty("elo.scraper.useStaticIndoorPoules", "true").toBoolean() &&
            StaticPoules.hasIndoorPoules(SeasonContext.indoor.seasonName)
        val sources = buildList {
            add(
                nextOutdoor to listOf(
                    listOf("KNKV-DISTRICT-LANDELIJK", "KORFBALL-VE-WK", "SN") to true,
                    // AI generated: the legacy OOST code now denotes nationwide B-category, not a region.
                    listOf("KNKV-DISTRICT-OOST", "KORFBALL-VE-WK", "RN") to false,
                )
            )
            if (!hasStaticIndoorPoules) {
                //glanerb|vaassen|rivalen|vakge|westerh|borcu|vroomsh
                add(
                    nextIndoor to listOf(
                        listOf("KNKV-DISTRICT-LANDELIJK", "KORFBALL-ZA-WK", "ZS") to true,
                        listOf("KNKV-DISTRICT-OOST", "KORFBALL-ZA-WK", "ZR") to false,
                        listOf("KNKV-DISTRICT-OOST", "KORFBALL-ZA-WK", "ZRB") to false,
                    )
                )
            }
        }

        val matches = sources.flatMap { (map, cats) ->
            cats.flatMap { (params, isProf) ->
                val (district, sport, category) = params
                api.pools(district, sport, category)
                    .poolsData.also { groups ->
                        println("KNKV $sport/$category: ${groups.sumOf { it.pools.size }} pools")
                    }.flatMap { pools ->
                        pools.pools.pmap { pool ->
                            val matches = mutableSetOf<Match>()
                            // AI generated: HTTP/envelope failures must propagate even for exceptional pools.
                            val standings = api.standing(pool.refId).firstOrNull()?.standings
                            if (standings == null && !("beker" in pools.division.name
                                        || isPostSeasonPool(pool.name)
                                        || listOf("ervallen", "Statistieken Reserveteams", "2-06").any { it in pool.name })) {
                                // AI generated: upcoming competitions can legitimately be unpublished.
                                val results = api.results(pool.refId, "2000-08-11", "2100-09-11")
                                val program = api.program(pool.refId, "2000-08-11", "2100-09-11")
                                check(results.isEmpty() && program.all { it.matches.isEmpty() }) {
                                    "No standings for published matches in ${pool.name} (${pool.refId}) in ${pools.division.name}"
                                }
                                println("KNKV: skipping unpublished pool ${pool.name} (${pool.refId})")
                                return@pmap emptySet()
                            }
                            val teams =
                                standings?.associate { it.team.name to it.stats.penalties.points } ?: run {
                                    if (!isPostSeasonPool(pool.name)) return@pmap emptySet()
                                    emptyMap()
                                }
                            val isFirst = teams.filter { it.key.endsWith(" 1") }

                            for (teamName in isFirst) {
                                nextActive.add(RankingNew.map(teamName.key))
                            }

                            if (isFirst.size >= 2 || teams.isEmpty() || isPostSeasonPool(pool.name)) {
                                val results = api.results(pool.refId, "2000-08-11", "2100-09-11")
                                    .filter { it.isFirstTeamMatch() }.map { it.toMatch() }
                                if (isPostSeasonPool(pool.name)) {
                                    nextSpecial.putAll(results.associateBy(Match::formatFixture))
                                }
                                matches.addAll(results)
                                if ((isProf && teams.isNotEmpty()) || isPostSeasonPool(pool.name)) {
                                    val program = api.program(pool.refId, "2000-08-11", "2100-09-11")
                                        .flatMap { it.matches }.filter { it.isFirstTeamMatch() }
                                        .filter { it.stats == null }.map { it.toMatch() }
                                    if (isPostSeasonPool(pool.name)) {
                                        for (match in (results + program)) {
                                            match.special = true
                                        }
                                    }
                                    map[pool.name] = (teams.mapKeys { (k) -> RankingNew.map(k) }) to (results + program)
                                }

                            }
                            matches
                        }
                            .flatten()
                    }
            }
        }

        if (hasStaticIndoorPoules) {
            val indoorSeasonName = SeasonContext.indoor.seasonName
            nextIndoor.putAll(StaticPoules.loadIndoorPoules(indoorSeasonName))
            nextActive.addAll(nextIndoor.values.flatMap { it.first.keys })
        }
        // AI generated: isolated live checks can write outputs outside the checkout.
        val outputDirectory = File(System.getProperty("elo.scraper.outputDirectory", "."))
        if (System.getProperty("elo.scraper.writeSpecialMatches", "true").toBoolean()) {
            writeOutput(File(outputDirectory, "web/public/specials${SeasonContext.indoor.seasonName}.json"), gson.toJson(nextSpecial))
        }
        val distinctMatches = matches.distinct()
        if (writeCurrentMatches) {
            writeOutput(File(outputDirectory, "matches/current.txt"),
                distinctMatches.sortedBy { it.format() }.joinToString("\n") { it.format() } + "\n")
        }
        outdoorPoules.clear()
        outdoorPoules.putAll(nextOutdoor)
        indoorPoules.clear()
        indoorPoules.putAll(nextIndoor)
        specialMatches.clear()
        specialMatches.putAll(nextSpecial)
        activeTeams.clear()
        activeTeams.addAll(nextActive)
        return distinctMatches
    }

    // AI generated: readers should never see a half-written output file.
    private fun writeOutput(file: File, content: String) {
        file.parentFile.mkdirs()
        val temporary = Files.createTempFile(file.parentFile.toPath(), "scrape-", ".tmp")
        try {
            Files.writeString(temporary, content)
            Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
