import { describe, expect, it } from 'vitest'

import { evaluateTransitionOutcome } from '@/simulator/DeclarativeSeasonTransition'
import {
  automaticChampionApplies,
  championshipPlayoffQualification,
  getWithdrawalReason,
  getSeasonTransition,
  relevantTeamsScopeForTeam,
  seasonOutcomePromotionApplies,
  seasonOutcomeRelegationApplies,
  teamNeedsExpandedEvaluation,
} from '@/simulator/SeasonTransitionRules'
import { Team } from '@/types'

describe('SeasonTransitionRules', () => {
  it('keeps a zaal 3k champion on the own-poule path but expands for a runner-up', () => {
    expect(teamNeedsExpandedEvaluation('zaal2526', '3k', '3A', 1, 8, 'Target')).toBe(false)
    expect(teamNeedsExpandedEvaluation('zaal2526', '3k', '3A', 2, 8, 'Target')).toBe(true)
    expect(teamNeedsExpandedEvaluation('zaal2627', '3k', '3A', 2, 8, 'Target')).toBe(true)
  })

  it('expands for playoff-driven and ranked outdoor branches', () => {
    expect(teamNeedsExpandedEvaluation('zaal2526', 'kl', 'KL', 1, 10, 'KL Team')).toBe(true)
    expect(teamNeedsExpandedEvaluation('veld2526vj', '4k', '4-01', 2, 4, '4K Team')).toBe(true)
  })

  it('builds relevant poules from declarative group dependencies', () => {
    const tiers = {
      'Korfbal League': { KL: ['KL Team'] },
      'Korfbal League 2': { KL2: ['KL2 Team'] },
      'Hoofdklasse': { HKA: ['HK Team'] },
      'Overgangsklasse': { OKA: ['OK Team'] },
      '1e Klasse': { '1A': ['1K Team'] },
      '2e Klasse': { '2A': ['2A Team'], '2B': ['2B Team'] },
      '3e Klasse': { '3A': ['Target', 'Mate'], '3B': ['Other'] },
    }

    const scope = relevantTeamsScopeForTeam('zaal2526', tiers, 'Target')

    expect(scope).not.toBeNull()
    expect(scope?.relevantPoules.sort()).toEqual(['2A', '2B', '3A', '3B'])
  })

  it('uses six promoted 3k runners-up for the 10-poule zaal 2627 third class', () => {
    const indoor = getSeasonTransition('zaal2627').definition
    const thirdClassRunnerUpRule = indoor.rules.find((rule) => rule.id === '3k-2-best6')

    expect(indoor.groups.find((group) => group.id === '3k')?.expectedPoules).toBe(10)
    expect(thirdClassRunnerUpRule?.count).toBe(6)
    expect(teamNeedsExpandedEvaluation('zaal2627', '1k', '1A', 7, 8, 'Target')).toBe(true)
    expect(teamNeedsExpandedEvaluation('zaal2627', '2k', '2A', 7, 8, 'Target')).toBe(true)
  })

  it('derives championship-playoff qualification from the declarative rules', () => {
    const indoor = getSeasonTransition('zaal2526').definition
    const outdoor = getSeasonTransition('veld2526vj').definition

    expect(championshipPlayoffQualification(indoor, 'kl', 'KL', 1)).toBe(true)
    expect(championshipPlayoffQualification(outdoor, 'ek', 'EK-01', 1)).toBe(true)
    expect(championshipPlayoffQualification(indoor, '3k', '3A', 1)).toBe(false)

    expect(automaticChampionApplies(indoor, 'kl', 'KL', 1)).toBe(false)
    expect(automaticChampionApplies(indoor, '3k', '3A', 1)).toBe(true)
  })

  it('uses veld-specific season outcome promotion semantics for ereklasse bars', () => {
    const outdoor = getSeasonTransition('veld2526vj').definition

    expect(seasonOutcomePromotionApplies(outdoor, 'ek', 'EK-01', 1, 'ek')).toBe(true)
    expect(seasonOutcomePromotionApplies(outdoor, 'ek', 'EK-01', 2, 'ek')).toBe(false)
    expect(seasonOutcomePromotionApplies(outdoor, 'ekd', 'EK-D-01', 1, 'ek')).toBe(false)
    expect(seasonOutcomePromotionApplies(outdoor, 'ekd', 'EK-D-01', 2, 'ek')).toBe(false)
    expect(seasonOutcomeRelegationApplies(outdoor, 'ekd', 'hk')).toBe(true)
  })

  it('treats a 7-team veld 2k poule as top-two up and last down', () => {
    const createTeam = (name: string) => new Team(
      name,
      1500,
      new Date('2026-03-29'),
      0,
      'Test',
      120,
      0.06,
      15,
    )
    const standingsByPoule = new Map([
      ['2-07', [
        [createTeam('A'), [12, 20, 40]],
        [createTeam('B'), [10, 15, 35]],
        [createTeam('C'), [8, 5, 30]],
        [createTeam('D'), [7, 0, 28]],
        [createTeam('E'), [6, -2, 25]],
        [createTeam('F'), [4, -10, 22]],
        [createTeam('G'), [2, -28, 15]],
      ]],
    ])

    expect(teamNeedsExpandedEvaluation('veld2526vj', '2k', '2-07', 2, 7, 'B')).toBe(false)

    const runnerUp = evaluateTransitionOutcome(
      'veld2526vj',
      'B',
      '2-07',
      standingsByPoule,
      {
        match: (teamA, teamB) => [teamA, teamB],
        playOffSeries: (teamA, teamB) => [teamA, teamB],
      },
    )
    const lastPlace = evaluateTransitionOutcome(
      'veld2526vj',
      'G',
      '2-07',
      standingsByPoule,
      {
        match: (teamA, teamB) => [teamA, teamB],
        playOffSeries: (teamA, teamB) => [teamA, teamB],
      },
    )

    expect(runnerUp.promote).toBe(true)
    expect(runnerUp.relegate).toBe(false)
    expect(lastPlace.promote).toBe(false)
    expect(lastPlace.relegate).toBe(true)
  })

  it('uses two promoted 4-team 4k runners-up before vacancy-chain fills', () => {
    const outdoor = getSeasonTransition('veld2526vj').definition
    const runnerUpRule = outdoor.rules.find((rule) => rule.id === '4k-2-best2')

    expect(runnerUpRule?.count).toBe(2)
    expect(runnerUpRule?.onlyUnassigned).toBe(true)
  })

  it('expands direct relegation when a vacancy chain can still save the team', () => {
    expect(teamNeedsExpandedEvaluation('veld2526vj', '3k', '3-05', 4, 4, 'EKCA')).toBe(true)

    const createTeam = (name: string) => new Team(
      name,
      1500,
      new Date('2026-05-09'),
      0,
      'Test',
      120,
      0.06,
      15,
    )
    const standing = (entries: Array<[string, [number, number, number]]>) =>
      entries.map(([name, stats]) => [createTeam(name), stats] as [Team, [number, number, number]])
    const standingsByPoule = new Map([
      ['2-04', standing([
        ['Devinco', [6, 14, 59]],
        ['HKC (U)', [4, 5, 53]],
        ['Sparta (N)', [4, 24, 78]],
        ['Wesstar', [0, -43, 48]],
      ])],
      ['2-12', standing([
        ['Viking', [6, 10, 50]],
        ['Keizer Karel', [4, 5, 45]],
        ['OJC \'98', [2, -4, 35]],
        ['NKV', [0, -11, 30]],
      ])],
      ['3-03', standing([
        ['SIOS / Leonidas', [5, 7, 50]],
        ['DTG', [5, 7, 49]],
        ['Lintjo', [2, 0, 41]],
        ['Noveas', [0, -14, 46]],
      ])],
      ['3-05', standing([
        ['Duko', [4, 12, 40]],
        ['Arena', [3, 3, 35]],
        ['Rivalen', [2, -2, 30]],
        ['EKCA', [1, -20, 39]],
      ])],
      ['3-06', standing([
        ['Winner', [6, 20, 50]],
        ['Runner', [4, 10, 40]],
        ['Third', [2, 0, 30]],
        ['Kesteren', [0, -6, 34]],
      ])],
      ['4-01', standing([
        ['MN en W', [10, 39, 80]],
        ['De Hoeve', [6, 16, 59]],
        ['Oerterp', [4, -11, 35]],
        ['Westergo', [2, -6, 30]],
        ['Lemmer', [2, -14, 28]],
        ['WIK \'34', [0, -11, 22]],
        ['SF Deinum', [0, -13, 20]],
      ])],
      ['4-02', standing([
        ['Vitesse (Be)', [6, 26, 60]],
        ['EKC 2000', [4, 0, 43]],
        ['WK', [2, 3, 30]],
        ['Zunobri', [0, -29, 20]],
      ])],
      ['4-03', standing([
        ['Stormvogels (U)', [4, -5, 45]],
        ['Ados', [4, 8, 34]],
        ['Aurora / DKV (IJ)', [2, 3, 30]],
        ['DSO (A)', [0, -6, 25]],
      ])],
      ['4-04', standing([
        ['Regio \'72', [6, 11, 60]],
        ['Olympia \'22', [6, 7, 47]],
        ['SDO (W)', [6, 19, 45]],
        ['KCD', [4, 8, 40]],
        ['SEV (Z)', [2, 3, 30]],
        ['TOP (V)', [0, -10, 20]],
        ['DKOD', [0, -38, 15]],
      ])],
      ['4-05', standing([
        ['Hebbes', [6, 11, 50]],
        ['Koveni', [4, 8, 40]],
        ['De Corvers', [2, 0, 30]],
        ['DOS (W)', [0, -19, 20]],
      ])],
      ['4-06', standing([
        ['Triade', [6, 31, 70]],
        ['SkunK', [4, 25, 43]],
        ['Attila', [4, -4, 36]],
        ['VIKO', [4, -1, 35]],
        ['KVS \'17', [4, 7, 34]],
        ['SKV (S)', [0, -16, 20]],
        ['Hemur Enge', [0, -42, 15]],
      ])],
      ['4-07', standing([
        ['\'t Capproen', [6, 35, 55]],
        ['Conventus', [4, 0, 37]],
        ['Olympia (S)', [2, -10, 30]],
        ['Nikantes', [0, -25, 20]],
      ])],
      ['4-08', standing([
        ['Focus', [5, 15, 50]],
        ['Voltreffers (O)', [4, 9, 40]],
        ['Scheldevogels', [3, 11, 35]],
        ['Terda', [0, -35, 20]],
      ])],
    ])
    const resolver = {
      match: (teamA: Team, teamB: Team) => [teamA, teamB] as [Team, Team],
      playOffSeries: (teamA: Team, teamB: Team) => [teamA, teamB] as [Team, Team],
    }

    expect(evaluateTransitionOutcome('veld2526vj', 'EKCA', '3-05', standingsByPoule, resolver).relegate).toBe(false)
    expect(evaluateTransitionOutcome('veld2526vj', 'Kesteren', '3-06', standingsByPoule, resolver).relegate).toBe(true)
  })

  it('exposes withdrawal reasons from season overrides', () => {
    expect(getWithdrawalReason('zaal2526', 'Keizer Karel')).toContain('Noviomagum')
    expect(getWithdrawalReason('veld2526vj', 'Keizer Karel')).toContain('Noviomagum')
    expect(getWithdrawalReason('zaal2526', 'SIOS / Leonidas')).toContain('WKS')
    expect(getWithdrawalReason('veld2526vj', 'De Hoeve')).toContain('WKS')
    expect(getWithdrawalReason('veld2526vj', 'Wesstar')).toContain('Duko')
    expect(getWithdrawalReason('zaal2526', 'Unknown Team')).toBeNull()
  })
})
