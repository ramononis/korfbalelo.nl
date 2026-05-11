import { defineStore } from 'pinia'
import type { MatchFixture } from '@/types'
import type { SimulatorResult } from '@/simulator/SeasonSimulator'
import usePoulesStore from '@/stores/poules'
import { ACTIVE_SEASON, type KnownSeasonName } from '@/season'
import { relevantTeamsScopeForTeam } from '@/simulator/SeasonTransitionRules'

export default defineStore('persistent', {
  state: (): {
    season: KnownSeasonName,
    simulationTeam: string | null,
    // season: 'zaal2425' | 'veld2425vj' | null,
    poule: string | null,
    match: MatchFixture | null,
    scoreInterval: { low: number, high: number },
    currentResult: SimulatorResult
  } => {
    return {
      season: ACTIVE_SEASON.seasonName,
      simulationTeam: null,
      // season: 'zaal2425',
      poule: null,
      match: null,
      scoreInterval: { low: -10, high: 10 },
      currentResult: new Map()
    }
  },
  persist: true,
  getters: {
    range(state): number[] {
      const low = Math.max(state.scoreInterval.low, -30)
      const high = Math.max(low, Math.min(state.scoreInterval.high, 30))
      return new Array(high - low + 1).fill(low).map((x, y) => x + y)
    },
    relevantTeams(state): {tierName: string, pouleName: string, relevantPoules: string[], relevantTeams: string[]} | null {
      if (!state.simulationTeam) throw Error('No team selected')
      const teamName = state.simulationTeam
      const selectedSeason = state.season
      const poulesStore = usePoulesStore()
      const tiers = poulesStore.tiers.get(selectedSeason)
      if (!tiers) {
        return null
      }
      const scope = relevantTeamsScopeForTeam(selectedSeason, tiers, teamName)
      if (!scope) {
        return null
      }
      return {
        tierName: scope.tierName,
        pouleName: scope.pouleName,
        relevantPoules: scope.relevantPoules,
        relevantTeams: scope.relevantTeams,
      }
    }
  },
});
