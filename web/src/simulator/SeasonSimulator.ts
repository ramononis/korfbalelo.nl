import Papa from 'papaparse'
import { type MatchFixture, type PouleData, Team } from '@/types'
import PoulePredicter, { type Executor, type StandingResult } from '@/simulator/PoulePredicter'
import axios from 'axios'
import type { KnownSeasonName } from '@/season'
import { evaluateTransitionOutcome } from '@/simulator/DeclarativeSeasonTransition'
import { findGroupForPoule, getSeasonTransition, teamNeedsExpandedEvaluation } from '@/simulator/SeasonTransitionRules'

export interface SimulatorParams {
  season: KnownSeasonName,
  team: string,
  relevantTeams: { tierName: string, pouleName: string, relevantPoules: string[], relevantTeams: string[] },
  match: MatchFixture,
  range: number[],
  teamsByName: Map<string, Team>,
  pouleData: Map<string, string>,
  meta: MetaData
}

export type Probabilities = {
  pChampion: number,
  pPromote: number,
  pRelegate: number,
  n: number
}
export type SimulatorResult = Map<number, Probabilities>

export type MetaData = {
  H: number,
  SD_A: number,
  SD_B: number,
  RD_PERIOD_DAYS: number,
  RD_MAX: number,
  MARGIN_RATING_SCALE?: number,
  MIN_AVERAGE_SCORE?: number,
  SCORE_SEASONALITY_MODE?: 'OFF' | 'MONTHLY_OFFSET',
  SCORE_SEASONALITY_MONTH_OFFSETS?: number[],
  MAX_ID: number,
}
export let metaData: MetaData

export async function fetchMetaData(): Promise<void> {
  if (!metaData) {
    await axios.get(`/meta.json`).then((response) => {
      metaData = response.data
    })
  }
}

export async function constructCalc(params: SimulatorParams): Promise<() => SimulatorResult> {
  const season = params.season
  const myTeam = params.team
  const { pouleName, relevantPoules, relevantTeams } = params.relevantTeams
  if (!relevantTeams.includes(params.match.home)) {
    throw new Error(`Deze wedstrijd is niet relevant voor ${myTeam}`)
  }

  const pouleData = new Map<string, PouleData>()
  metaData = params.meta
  await Promise.all(relevantPoules.map(pouleName => {
    return new Promise<void>((resolve) => {
      Papa.parse(params.pouleData.get(pouleName)!, {
        delimiter: '\t',
        skipEmptyLines: true,
        dynamicTyping: true,
        complete: (results) => {
          const data = results.data as string[][]
          if (!data.length) {
            resolve()
            return
          }
          const latestRow = data[data.length - 1]
          if (!latestRow || typeof latestRow[1] !== 'string') {
            resolve()
            return
          }
          pouleData.set(pouleName, JSON.parse(latestRow[1]) as PouleData)
          resolve()
        }
      })
    })
  }))
  const specialMatchesGlobal: MatchFixture[] = []
  const executors: Map<number, Map<string, Executor>> = new Map()
  const range = params.range
  for (const [, poule] of pouleData) {
    specialMatchesGlobal.push(...poule.results.filter(it => it.special))
  }
  for (const m of range) {
    const pp = new Map<string, Executor>()
    for (const [pouleName, poule] of pouleData) {
      const fixture = { ...params.match } as MatchFixture
      fixture.homeScore = 1000 + m
      fixture.awayScore = 1000

      pp.set(pouleName, new PoulePredicter(
        season,
        pouleName,
        new Map<string, number>(poule.standing.map((standingEntry) => {
          return [standingEntry.team.name, standingEntry.stats.penalties.points]
        })),
        [...poule.results, fixture],
        poule.fixtures,
        params.teamsByName
      ).executor())
    }
    executors.set(m, pp)
  }

  return () => {
    const start = performance.now()
    const result = new Map<number, Probabilities>(
      range.map(m => [m, {
        pChampion: 0,
        pPromote: 0,
        pRelegate: 0,
        n: 0
      }])
    )
    while (performance.now() - start < 500) {
      for (const m of range) {
        const resultM = result.get(m)!
        resultM.n++
        const pps: Map<string, Executor> = executors.get(m)!
        const results = new Map<string, StandingResult>()
        const ownResult = pps.get(pouleName)!()
        results.set(pouleName, ownResult)
        const ownPosition = ownResult.findIndex(([team]) => team.name === myTeam)
        const specialMatches = [...specialMatchesGlobal]
        const fixture = { ...params.match } as MatchFixture
        fixture.homeScore = 1000 + m
        fixture.awayScore = 1000
        if (fixture.special) specialMatches.push(fixture)

        function specialMatch(t1: Team, t2: Team, d: string): [Team, Team] | null {
          const found = specialMatches.find(m => m.special && m.home === t1.name && m.away === t2.name && d === m.date)
          if (found) {
            return found.homeScore > found.awayScore ? [t1, t2] : [t2, t1]
          }
          return null
        }

        function match(t1: Team, t2: Team, d: string, neutral: boolean = false): [Team, Team] {
          const preset = specialMatch(t1, t2, d)
          if (preset) return preset
          t1.sampleRating(d)
          t2.sampleRating(d)
          const hPower = neutral ? 0.0 : 1.0
          const e1 = 1.0 / (1.0 + Math.pow(10.0, t2.rating - t1.rating - metaData.H * hPower) / 400.0)
          return (Math.random() < e1) ? [t1, t2] : [t2, t1]
        }

        function playOffSeries(t1: Team, t2: Team, dates: string[]): [Team, Team] {
          const [d1, d2, d3] = dates
          if (!d1 || !d2 || !d3) {
            throw new Error(`best_of_3 needs exactly 3 dates for ${t1.name} vs ${t2.name}`)
          }
          const [w1, l1] = match(t1, t2, d1)
          const [w2] = match(t2, t1, d2)
          return w1 === w2 ? [w1, l1] : match(t1, t2, d3, true)
        }

        function doOthers() {
          for (const [pouleNameOther, executor] of pps) {
            if (pouleNameOther === pouleName) continue
            results.set(pouleNameOther, executor())
          }
        }
        if (ownPosition < 0) {
          continue
        }
        const transition = getSeasonTransition(season)
        const sourceGroupId = findGroupForPoule(transition.definition, pouleName).id
        const needsExpandedEvaluation = teamNeedsExpandedEvaluation(
          season,
          sourceGroupId,
          pouleName,
          ownPosition + 1,
          ownResult.length,
          myTeam,
        )
        if (needsExpandedEvaluation) {
          doOthers()
        }
        const outcome = evaluateTransitionOutcome(
          season,
          myTeam,
          pouleName,
          results,
          { match, playOffSeries },
        )
        if (outcome.champion) {
          resultM.pChampion++
        }
        if (outcome.promote) {
          resultM.pPromote++
        }
        if (outcome.relegate) {
          resultM.pRelegate++
        }
      }
    }
    return result
  }
}
