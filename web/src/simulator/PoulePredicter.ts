import { type MatchFixture, Team } from '@/types'
import { distroBetween, sampleMatch } from '@/utils'
import { toRaw } from 'vue'
import { isOutdoorSeasonName } from '@/season'

export type StandingResult = [Team, [number, number, number]][]
export type Executor = () => StandingResult
export default class PoulePredicter {
  public teamNames: string[]
  public teams: Team[]
  public n: number
  public nMatches: number
  public baseMatches: Array<[number, number, Date] | undefined>
  public order: [Date, number][] = []
  public startPoints: Int16Array
  public startBalance: Int16Array
  public startScored: Int16Array
  public singleRoundRobin: boolean
  private teamIndexByName: Map<string, number>

  constructor(
    public season: string,
    public pouleName: string,
    public teamsToPenalty: Map<string, number>,
    public results: MatchFixture[],
    public fixtures: MatchFixture[],
    teamsByName: Map<string, Team>,
  ) {
    this.teamNames = [...this.teamsToPenalty.keys()]
    this.teams = this.teamNames.map((teamName) => {
      const team = teamsByName.get(teamName)
      if (!team) {
        throw new Error(`Unknown team ${teamName} in poule ${pouleName}`)
      }
      return toRaw(team) as Team
    })
    this.teamIndexByName = new Map(this.teamNames.map((teamName, index) => [teamName, index]))
    this.n = this.teamNames.length
    this.startPoints = new Int16Array(this.n)
    this.startBalance = new Int16Array(this.n)
    this.startScored = new Int16Array(this.n)
    this.singleRoundRobin = isOutdoorSeasonName(season) && this.n === 7
    this.baseMatches = new Array(this.n * this.n)
    for (const fixture of fixtures) {
      if (fixture.special) continue
      const h = this.teamIndexByName.get(fixture.home)
      const a = this.teamIndexByName.get(fixture.away)
      if (h === undefined || a === undefined) continue
      this.baseMatches[h * this.n + a] = [-1, -1, new Date(fixture.date)]
    }
    for (const result of results) {
      if (result.special) continue
      const h = this.teamIndexByName.get(result.home)
      const a = this.teamIndexByName.get(result.away)
      if (h === undefined || a === undefined) continue
      this.baseMatches[h * this.n + a] = [result.homeScore, result.awayScore, new Date(result.date)]
      const balance = result.homeScore - result.awayScore
      const homePoints = Math.sign(balance) + 1
      this.startPoints[h] = (this.startPoints[h] ?? 0) + homePoints
      this.startPoints[a] = (this.startPoints[a] ?? 0) + (2 - homePoints)
      this.startBalance[h] = (this.startBalance[h] ?? 0) + balance
      this.startBalance[a] = (this.startBalance[a] ?? 0) - balance
      this.startScored[h] = (this.startScored[h] ?? 0) + result.homeScore
      this.startScored[a] = (this.startScored[a] ?? 0) + result.awayScore
    }
    if (!this.baseMatches.some((match) => match)) {
      for (let i = 0; i < this.baseMatches.length; i++) {
        const h = (i / this.n) | 0
        const a = i % this.n
        if (h === a) continue
        if (!this.singleRoundRobin || a > h) {
          this.baseMatches[i] = [-1, -1, new Date()]
        }
      }
    }
    this.nMatches = this.baseMatches.filter((x) => x).length
    for (let i = 0; i < this.baseMatches.length; i++) {
      if (i % (this.n + 1) === 0) continue
      const match = this.baseMatches[i]
      if (!match) continue
      const [h, , date] = match
      if (h == -1) {
        this.order.push([date, i])
      }
    }
    this.order.sort((a, b) => a[0].getTime() - b[0].getTime())

    this.teamNames.forEach((teamName, i) => {
      const penalty = this.teamsToPenalty.get(teamName) ?? 0
      this.startPoints[i] = (this.startPoints[i] ?? 0) - penalty
    })
  }

  public executor(): Executor {
    const n = this.n
    const singleRoundRobin = this.singleRoundRobin
    const teamNames = this.teamNames
    const matches: Array<[number, number, Date] | undefined> = this.baseMatches
      .map((match) => (match ? [...match] as [number, number, Date] : undefined))
    const teamsCopy: Team[] = new Array(n)
    const points: Int16Array = new Int16Array(this.n)
    const balance: Int16Array = new Int16Array(this.n)
    const scored: Int16Array = new Int16Array(this.n)
    const addStat = (arr: Int16Array, index: number, value: number) => {
      arr[index] = (arr[index] ?? 0) + value
    }

    function doMatch(m: number, date: Date) {
      const hI = (m / n) | 0
      const aI = m % n
      const h = teamsCopy[hI]!
      const a = teamsCopy[aI]!
      h.sampleRating(date)
      a.sampleRating(date)
      const distro = distroBetween(h, a, singleRoundRobin, date)
      const [sh, sa] = sampleMatch(distro)
      matches[m] = [sh, sa, date]
      const bal = sh - sa
      const homePoints = Math.sign(bal) + 1
      addStat(points, hI, homePoints)
      addStat(points, aI, 2 - homePoints)
      addStat(balance, hI, bal)
      addStat(balance, aI, -bal)
      addStat(scored, hI, sh)
      addStat(scored, aI, sa)
    }
    return () => {
      points.set(this.startPoints)
      balance.set(this.startBalance)
      scored.set(this.startScored)
      for (let i = 0; i < this.teams.length; i++) {
        const team = this.teams[i]!
        teamsCopy[i] = new Team(
          team.name,
          team.rating,
          team.lastMatchDate,
          0.0,
          "",
          team.rd,
          team.rv,
          team.averageScore
        )
      }
      for (const [date, mi] of this.order) {
        doMatch(mi, date)
      }
      const matchesCount = this.nMatches || 1
      return rank(Array.from(Array(n).keys()), points, resolvePointsTie)
        .map((it) => [
          teamsCopy[it]!,
          [
            (points[it] ?? 0) * n / matchesCount,
            (balance[it] ?? 0) * n / matchesCount,
            (scored[it] ?? 0) * n / matchesCount]
          ]
        )
    }

    function rank(teams: number[], scores: Int16Array, resolver: (duplicates: number[]) => number[]): number[] {
      let highest = -1000000
      let prevHighest = 1000000
      let duplicates: number[] = []
      const ranking: number[] = []
      let nRanked = 0
      while (nRanked < teams.length) {
        for (const ti of teams) {
          const s = scores[ti] ?? Number.NEGATIVE_INFINITY
          if (highest < s && s < prevHighest) {
            duplicates = [ti]
            highest = s
          } else if (s === highest) {
            duplicates.push(ti)
          }
        }
        if (duplicates.length > 1) {
          ranking.push(...resolver(duplicates))
        } else if (duplicates.length === 1) {
          const [only] = duplicates
          if (only !== undefined) {
            ranking.push(only)
          }
        } else {
          throw Error(`Could not rank remaining teams ${teams.join(',')} with scores ${Array.from(scores).join(',')}`)
        }
        nRanked += duplicates.length
        duplicates = []
        prevHighest = highest
        highest = -1000000
      }
      return ranking
    }
    function resolvePointsTie(ints: number[]): number[] {
      const subPoints = new Int16Array(n)
      const subScored = new Int16Array(n)
      const subBalance = new Int16Array(n)
      for (const h of ints) {
        for (const a of ints) {
          if (h !== a) {
            const m = matches[h * n + a]
            if (!m) continue
            const bal = m[0] - m[1]
            const pts = Math.sign(bal) + 1
            addStat(subPoints, h, pts)
            addStat(subPoints, a, 2 - pts)
            addStat(subBalance, h, bal)
            addStat(subBalance, a, -bal)
            addStat(subScored, h, m[0])
            addStat(subScored, a, m[1])
          }
        }
      }

      function resolveSubScoredTie(subInts: number[]): number[] {
        return subInts.length === ints.length ? rank(subInts, balance, resolveBalanceTie) : resolvePointsTie(subInts)
      }

      function resolveSubBalanceTie(subInts: number[]): number[] {
        return subInts.length === ints.length ? rank(subInts, subScored, resolveSubScoredTie) : resolvePointsTie(subInts)
      }

      function resolveSubPointsTie(subInts: number[]): number[] {
        return subInts.length  === ints.length ? rank(subInts, subBalance, resolveSubBalanceTie) : resolvePointsTie(subInts)
      }
      return rank(ints, subPoints, resolveSubPointsTie)
    }

    function resolveBalanceTie(ints: number[]): number[] {
      return rank(ints, scored, resolveScoredTie)
    }

    function resolveScoredTie(ints: number[]): number[] {
      const scores = new Int16Array(n)
      const currDuplicates = [...ints]
      let score = n
      while (currDuplicates.length > 1) {
        const strengths = currDuplicates.map((it) => Math.pow(teamsCopy[it]!.rating, ints.length))
        const sum = strengths.reduce((a, b) => a + b, 0)
        const normalized = strengths.map((it) => it / sum)
        let current = 0.0
        const random = Math.random()
        let index = 0
        for (const d of normalized) {
          current += d
          if (random <= current) {
            const selected = currDuplicates[index]
            if (selected === undefined) {
              break
            }
            addStat(scores, selected, score)
            score--
            currDuplicates.splice(index, 1)
            break
          }
          index++
        }
      }
      return rank(ints, scores, (tiedByRandomScore) =>
        [...tiedByRandomScore].sort((a, b) => teamNames[a]!.localeCompare(teamNames[b]!)),
      )
    }
  }
}
