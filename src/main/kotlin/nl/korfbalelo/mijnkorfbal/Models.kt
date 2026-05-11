package nl.korfbalelo.mijnkorfbal

import com.google.gson.annotations.SerializedName
import nl.korfbalelo.elo.RankingNew
import java.time.ZonedDateTime

data class PoolsResult(val poolsData: List<PoolsData>)
data class Division(val name: String)
data class PoolsData(val division: Division, val pools: List<Pool>)
data class Pool(val name: String, @field:SerializedName("ref_id") val refId: Int)

typealias StandingResult = List<Standings>
typealias ResultsResult = List<WeekResults>

data class WeekResults(val week: Int, val matches: List<Match>)
data class Standings(val standings: List<Standing>)
data class Standing(val team: Team, val stats: StandingStats)

data class StandingStats(
    val penalties: StandingPenalties,
    val position: Int,
    val points: Int,
    val goals: GoalsStats,
    val won: Int,
    val draw: Int,
    val lost: Int,
)
data class GoalsStats(val `for`: Int, val against: Int)

data class StandingPenalties(val points: Int)
//TODO ignore name for gson:

data class Team(var name: String)
data class Match(val teams: MatchTeams, val stats: MatchStats?, val date: String) {
    fun toMatch() = nl.korfbalelo.elo.Match(
            RankingNew.map(teams.home.name),
            RankingNew.map(teams.away.name),
            stats?.home?.score ?: -1,
            stats?.away?.score ?: -1,
            ZonedDateTime.parse(date.replace("+0200", "+02:00").replace("+0100", "+01:00")).toLocalDate()
        )

}
data class MatchStats(val home: TeamMatchStats, val away: TeamMatchStats)
data class TeamMatchStats(val score: Int)
data class MatchTeams(val home: Team, val away: Team)
