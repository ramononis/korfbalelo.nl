package nl.korfbalelo.mijnkorfbal

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.gson.responseObject as responseObjectOriginal
import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nl.korfbalelo.elo.Match
import nl.korfbalelo.elo.RankingNew
import nl.korfbalelo.elo.SeasonContext
import nl.korfbalelo.elo.gson
import nl.korfbalelo.elo.ot
import nl.korfbalelo.elo.pmap
import java.io.File
import java.lang.NullPointerException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

typealias PouleData = Pair<Map<String, Int>, List<Match>>

object Scraper {
    var forceNetwork = System.getProperty("elo.scraper.forceNetwork", "false").toBoolean()
    val outdoorPoules = ConcurrentHashMap<String, PouleData>()
    val indoorPoules = ConcurrentHashMap<String, PouleData>()
    val specialMatches = ConcurrentHashMap<String, Match>()

    internal fun isPostSeasonPool(poolName: String): Boolean =
        listOf("play", "eindfase").any { marker ->
            poolName.contains(marker, ignoreCase = true)
        }

    private fun addMissingOutdoorTeam(pouleName: String, teamName: String) {
        outdoorPoules[pouleName]?.let { (teams, matches) ->
            if (teamName !in teams) {
                // Temporary manual correction until Mijn Korfbal publishes the transfer.
                outdoorPoules[pouleName] = (teams + (teamName to 0)) to matches
                activeTeams.add(teamName)
            }
        }
    }

    private fun applyTemporaryOutdoorPouleFixes() {
        addMissingOutdoorTeam("3-03", "DTG")
        addMissingOutdoorTeam("4-01", "De Hoeve")
    }

    inline fun <reified T : Any> Request.responseObject(forceNetwork: Boolean = false) =
        File("cache/${(this.url.hashCode() + parameters.hashCode()).absoluteValue}.txt").let { f ->
            if (f.exists() && !forceNetwork) {
                Gson().fromJson(f.reader(), object : TypeToken<T>() {}.type)
            } else {
                responseObjectOriginal<T>().also {
                    f.parentFile?.mkdirs()
                    f.writeText(Gson().toJson(it.third.get()))
                }.third.get()
            }
        }

    val activeTeams = mutableSetOf<String>()
    @JvmStatic
    fun main(args: Array<String>) {
        scrapePoules()
    }

    fun scrapePoules(): List<Match> {
        val hasStaticIndoorPoules = StaticPoules.hasIndoorPoules(SeasonContext.indoor.seasonName)
        val sources = buildList {
            add(
                outdoorPoules to listOf(
                    listOf("KNKV-DISTRICT-LANDELIJK", "KORFBALL-VE-WK", "StV") to true,
                    listOf("KNKV-DISTRICT-OOST", "KORFBALL-VE-WK", "RV") to false,
                    listOf("KNKV-DISTRICT-LANDELIJK", "KORFBALL-VE-WK", "Beker") to false,
                )
            )
//        outdoorPoules to listOf(
//            listOf("KNKV-DISTRICT-LANDELIJK", "KORFBALL-VE-WK", "SN") to true,
//            listOf("KNKV-DISTRICT-OOST", "KORFBALL-VE-WK", "RN") to false,
////            listOf("KNKV-DISTRICT-LANDELIJK", "KORFBALL-VE-WK", "Beker") to false
//        ),
            if (!hasStaticIndoorPoules) {
                //glanerb|vaassen|rivalen|vakge|westerh|borcu|vroomsh
                add(
                    indoorPoules to listOf(
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
                val paramList = listOf("district" to district, "sport" to sport, "category" to category)
                "https://api-mijn.korfbal.nl/api/v2/pools".httpGet(paramList).responseObject<PoolsResult>(forceNetwork)
                    .poolsData.flatMap { pools ->
//                    if (pools.division.name.contains(Regex("[^-]beker")))
//                        return@flatMap emptySet<Match>()
                        pools.pools.pmap { pool ->
                            val matches = mutableSetOf<Match>()
                            val standings = runCatching {
                                "https://api-mijn.korfbal.nl/api/v2/matches/pools/${pool.refId}/standing".httpGet()
                                    .responseObject<StandingResult>(forceNetwork)
                                    .first().standings
                            }.getOrElse {
                                if ("beker" in pools.division.name
                                    || isPostSeasonPool(pool.name)
                                    || listOf(
                                        "ervallen",
//                                    "Play-down KKL",
//                                    "Play-off KL2",
//                                    "Statistieken",
//                                    "EK play-off",
                                        "Statistieken Reserveteams",
//                                    "S-053",
////                                    "979",
////                                    "996"
                                    ).any { it in pool.name }
                                ) null else
                                    run {
                                        Thread.yield()
                                        error("WTF + $pool")
                                    }
                            }
                            val teams =
                                standings?.associate { it.team.name to it.stats.penalties.points } ?: run {
                                    if (!isPostSeasonPool(pool.name)) return@pmap emptySet()
                                    emptyMap()
                                }
                            val isFirst = teams.filter { it.key.endsWith(" 1") }

                            for (teamName in isFirst) {
                                activeTeams.add(RankingNew.map(teamName.key))
                            }

                            if (isFirst.size >= 2 || teams.isEmpty() || isPostSeasonPool(pool.name)) {
                                val matchesResult =
                                    "https://api-mijn.korfbal.nl/api/v2/matches/pools/${pool.refId}/results?dateFrom=2000-08-11&dateTo=2100-09-11".httpGet()
                                        .responseObject<ResultsResult>(forceNetwork)
                                val results = try {
                                    matchesResult.flatMap { it.matches }.filter {
                                        it.teams.home.name.endsWith(" 1") && it.teams.away.name.endsWith(" 1")
                                    }.filter { it.stats != null }.map { it.toMatch() }
                                } catch (e: NullPointerException) {
                                    error("WTF")
                                }
                                if (isPostSeasonPool(pool.name)) {
                                    specialMatches.putAll(results.associateBy(Match::formatFixture))
                                }
                                matches.addAll(results)
                                if (isProf && teams.isNotEmpty() || isPostSeasonPool(pool.name)) {
                                    val matchesProgram =
                                        "https://api-mijn.korfbal.nl/api/v2/matches/pools/${pool.refId}/program?dateFrom=2000-08-11&dateTo=2100-09-11".httpGet()
                                            .responseObject<ResultsResult>(forceNetwork)
                                    val program = try {
                                        matchesProgram.flatMap { it.matches }.filter {
                                            it.teams.home.name.endsWith(" 1") && it.teams.away.name.endsWith(" 1")
                                        }.filter { it.stats == null }.map { it.toMatch() }
                                    } catch (e: NullPointerException) {
                                        error("WTF")
                                    }
                                    standings?.forEach { it.team.name = RankingNew.map(it.team.name) }
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
            indoorPoules.putAll(StaticPoules.loadIndoorPoules(indoorSeasonName))
            activeTeams.addAll(indoorPoules.values.flatMap { it.first.keys })
        }
        applyTemporaryOutdoorPouleFixes()
        File("web/public/specials${SeasonContext.indoor.seasonName}.json").writeText(gson.toJson(specialMatches))
        File("matches/current.txt")
            .writeText(
                matches.sortedBy { it.format() }
                    .joinToString("\n") { (h, a, hS, aS, d) ->
                        listOf(d, h, a, hS, aS).joinToString(",")
                    } + "\n")
        return matches
    }
}
