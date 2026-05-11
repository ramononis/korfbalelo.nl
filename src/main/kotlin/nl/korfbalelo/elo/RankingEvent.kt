package nl.korfbalelo.elo

import nl.korfbalelo.elo.ApplicationNew.log
import nl.korfbalelo.elo.RankingNew.add
import nl.korfbalelo.elo.RankingNew.aliases
import nl.korfbalelo.elo.RankingNew.ranking
import nl.korfbalelo.elo.RankingNew.rebalance
import org.apache.commons.math3.distribution.NormalDistribution
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation
import java.time.LocalDate
import kotlin.math.max

val commandRegex = Regex("""(\d{4}-\d{2}-\d{2})\s+(\w+)\s+(.*)""")
val arrowRegex = Regex("""(.*) > (.*)""")
val doubleArrowRegex = Regex("""(.*) > (.*) > (.*)""")

abstract class RankingEvent : Runnable {
    abstract val date: LocalDate
}

fun parseEvent(str: String): RankingEvent {

    if (str.startsWith("//")) return NoopEvent(LocalDate.ofEpochDay(0))
    val (_, dateStr, cmd, args) = commandRegex.matchEntire(str)?.groupValues ?: error(str)
    val date = LocalDate.parse(dateStr)
    return try {
        when (cmd) {
            "spawn" -> {
                val (_, name, place) = arrowRegex.matchEntire(args)!!.groupValues
                SpawnEvent(date, name, place)
            }

            "ack" -> AckEvent(date, args)
            "xack" -> AckEvent(date, args)
            "end" -> EndEvent(date, args)
            "replace" -> {
                val (_, old, new) = arrowRegex.matchEntire(args)!!.groupValues
                MergeEvent(date, new, null, setOf(old))
            }

            "xalias" -> {
                val (_, old, new) = arrowRegex.matchEntire(args)!!.groupValues
                AliasEvent(date, old, new)
            }

            "endalias" -> EndAliasEvent(date, args)
            "merge" -> {
                val (_, olds, new, place) = doubleArrowRegex.matchEntire(args)!!.groupValues
                val oldNames = olds.split(",")
                MergeEvent(date, new, place, oldNames)
            }

            else -> error("unknown command: $cmd")
        }
    } catch (npe: NullPointerException) {
        error(str)
    }
}

const val LOWEST_ELO = 0
const val CONSTANT = 1
const val SECOND_LOWEST_ELO = 2
const val THIRD_LOWEST_ELO = 3
const val PERCENTILE_90 = 4
const val SPAWN_TYPE = PERCENTILE_90
val sd = StandardDeviation()

data class SpawnEvent(override val date: LocalDate, val name: String, val place: String) : RankingEvent() {

    override fun toString() = "$date $name"
    override fun run() {
        if (name in ranking) error("$name already exists")
        val eloOrOffset = ApplicationNew.startRating(this) ?: 0.0

        add(Team(name, place, Team.MAGIC_1500).also {
            it.startOffset = eloOrOffset
            it.created = date
            if (log) {
                if (it.graphName in originMap) error("WTF $this")
                originMap[it.graphName] = mutableSetOf(it.graphName)
            }
        })
    }
}
data class NoopEvent(override val date: LocalDate) : RankingEvent() {
    override fun run() = Unit
}

data class EndEvent(override val date: LocalDate, val name: String) : RankingEvent() {
    override fun run() {
        if (name !in ranking) error("$name doesn't exist")
        val team = ranking[name]!!
        if (team.lastDate!!.plusDays(1) != date) error("wrong end $name : ${ranking[name]}")
        DiscontinuedTeams.registerEnd(team, date)
        RankingNew.remove(name)
    }
}

data class EndAliasEvent(override val date: LocalDate, val name: String) : RankingEvent() {
    override fun run() {
        if (name !in aliases) error("$name doesn't exist")
        aliases.remove(name)
    }
}

var maxOrigins = 1
var originMap = mutableMapOf<String, MutableSet<String>>()
data class MergeEvent(
    override val date: LocalDate,
    val name: String,
    val place: String?,
    val oldNames: Collection<String>,
) : RankingEvent() {
    override fun run() {
        val oldNameList = oldNames.toList()
        val oldTeams = oldNames.map(ranking::getValue)
        val maxRanking = oldTeams.maxBy(Team::rating)
        oldTeams.forEach {
            it.setNewRD(date)
        }
        val (rating, rd) =
//            if (oldTeams.size == 1)
                maxRanking.rating to maxRanking.rd
//            else
//                CDFUtil.approximateMeanAndStdDev(maxRanking.rating, maxRanking.rd) {
//                var lol = 1.0
//                for (t in oldTeams) {
//                    lol *= (t.rating to t.rd).cdf(it)
//                }
//                lol
//            }
        val oldGraphNames = oldTeams.map(Team::graphName)
        val pl = place ?: ranking[oldNames.first()]!!.place
        val keepStats = name in oldNames
        oldNameList.forEach { oldName ->
            val oldTeam = ranking.getValue(oldName)
            if (!(keepStats && oldName == name)) {
                when {
                    oldNameList.size == 1 -> DiscontinuedTeams.registerRename(oldTeam, date, name)
                    keepStats -> DiscontinuedTeams.registerMergedInto(oldTeam, date, name)
                    else -> DiscontinuedTeams.registerFusedIntoNew(
                        team = oldTeam,
                        eventDate = date,
                        otherTeams = oldNameList.filterNot { it == oldName },
                        newName = name,
                    )
                }
            }
            RankingNew.remove(oldName)
        }
        if (name in ranking) error("$name already exists")
        val mergedTeam = if (keepStats) oldTeams.first { it.name == name } else Team(name, pl, Team.MAGIC_1500)
        add(mergedTeam.also {
            it.name = name
            it.place = pl
            it.rating = rating
            it.rd = rd
            it.rv = oldTeams.map { it.rv }.average()
            if (!keepStats) {
                it.games = 0
                it.averageScore = 5.0
                it.currentDiff = 0.0
                it.origins = 1
                it.startOffset = 0.0
                it.created = date
                it.lastDate = null
                it.firstMatchDate = null
                it.lastMatchDate = null
                it.opponents.clear()
                it.graphSeriesLabel = if (RankingNew.graph.hasTeam(it.fullName)) {
                    "${it.fullName} [r:$date]"
                } else {
                    null
                }
            }
//            if (7 <= it.origins) {
//                println(it)
//                maxOrigins = it.origins
//            }
            if (log) {
                if (it.graphName in originMap && it.name !in oldNames) error("WTF $this")
                val mergedOrigins = oldGraphNames.map { originMap[it]!! }
                    .fold(mutableSetOf(it.graphName)) { acc, source ->
                        acc.addAll(source)
                        acc
                    }
                originMap[it.graphName] = mergedOrigins
                mergedOrigins.forEach { source ->
                    originMap[source] = mergedOrigins
                }
            }
        })
    }
}

data class AliasEvent(override val date: LocalDate, val fromName: String, val toName: String) : RankingEvent() {
    override fun run() {
        if (fromName in ranking) error("$fromName already exists")
        if (fromName in aliases) error("$fromName already aliased")
        if (toName !in ranking) error("$toName doesn't exist")
        aliases[fromName] = toName
    }
}

data class AckEvent(override val date: LocalDate, val name: String) : RankingEvent() {
    override fun run() {
        if (name !in ranking) error("$name doesn't exist")
        ranking[name]!!.setNewRD(date)
    }
}
