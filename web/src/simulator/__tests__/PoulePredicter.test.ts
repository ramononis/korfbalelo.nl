import { describe, expect, it } from 'vitest'

import PoulePredicter from '@/simulator/PoulePredicter'
import { Team, type MatchFixture } from '@/types'

function teamsByName(names: string[]): Map<string, Team> {
  return new Map(names.map((name, index) => [
    name,
    new Team(name, 1000 + index, new Date('2026-05-09'), 0, 'Test', 120, 0.06, 15),
  ]))
}

function predicter(season: string, pouleName: string, names: string[]): PoulePredicter {
  return new PoulePredicter(
    season,
    pouleName,
    new Map(names.map((team) => [team, 0])),
    [],
    [],
    teamsByName(names),
  )
}

function fixture(home: string, away: string): MatchFixture {
  return {
    date: '2026-05-09',
    home,
    away,
    homeScore: -1,
    awayScore: -1,
    pHome: 0,
    pDraw: 0,
    pAway: 0,
    guessHome: 0,
    guessAway: 0,
    homeDiff: 0,
    awayDiff: 0,
    homeRating: 0,
    awayRating: 0,
    homeRd: 0,
    awayRd: 0,
  }
}

describe('PoulePredicter', () => {
  it('uses single round-robin simulation for 7-team outdoor poules', () => {
    const sevenTeams = ['A', 'B', 'C', 'D', 'E', 'F', 'G']
    const fourTeams = ['A', 'B', 'C', 'D']

    expect(predicter('veld2526vj', '4-04', sevenTeams).singleRoundRobin).toBe(true)
    expect(predicter('veld2627nj', '4-04', sevenTeams).singleRoundRobin).toBe(true)
    expect(predicter('veld2526vj', '2-07', sevenTeams).singleRoundRobin).toBe(true)
    expect(predicter('zaal2526', '3D', sevenTeams).singleRoundRobin).toBe(false)
    expect(predicter('veld2526vj', '4-05', fourTeams).singleRoundRobin).toBe(false)
  })

  it('does not synthesize missing reverse fixtures when schedule data exists', () => {
    const names = ['A', 'B', 'C', 'D', 'E', 'F', 'G']
    const fixtures = names.flatMap((home, homeIndex) =>
      names
        .slice(homeIndex + 1)
        .map((away) => fixture(home, away)),
    )

    const poule = new PoulePredicter(
      'veld2526vj',
      '4-04',
      new Map(names.map((team) => [team, 0])),
      [],
      fixtures,
      teamsByName(names),
    )

    expect(poule.order).toHaveLength(fixtures.length)
  })
})
