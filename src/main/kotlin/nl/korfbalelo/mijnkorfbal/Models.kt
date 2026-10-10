package nl.korfbalelo.mijnkorfbal

import com.google.gson.annotations.SerializedName
import nl.korfbalelo.elo.RankingNew
import java.time.LocalDate

data class PoolsResult(val poolsData: List<PoolsData>)
data class Division(val name: String)
data class PoolsData(val division: Division, val pools: List<Pool>)
data class Pool(val name: String, @field:SerializedName("ref_id") val refId: Int)

typealias StandingResult = List<Standings>
// AI generated: results are flat; the programme still has numeric week groups.
typealias ResultsResult = List<CompetitionMatch>
typealias ProgramResult = List<WeekProgram>

data class WeekProgram(val week: Int, val matches: List<CompetitionMatch>)
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
// AI generated: keep network team names and local calendar dates separate from Elo models.
data class CompetitionTeam(val teamName: String)
data class CompetitionMatch(
    val homeTeam: CompetitionTeam,
    val awayTeam: CompetitionTeam,
    val stats: MatchStats?,
    val date: String,
) {
    fun isFirstTeamMatch() = homeTeam.teamName.endsWith(" 1") && awayTeam.teamName.endsWith(" 1")

    fun toMatch() = nl.korfbalelo.elo.Match(
            RankingNew.map(homeTeam.teamName),
            RankingNew.map(awayTeam.teamName),
            stats?.home?.score ?: -1,
            stats?.away?.score ?: -1,
            LocalDate.parse(date)
        )

}
data class MatchStats(val home: TeamMatchStats, val away: TeamMatchStats)
data class TeamMatchStats(val score: Int)
