import type { KnownSeasonName } from '@/season'

import indoor2526DefinitionJson from '@rules/pd/zaal2526__zaal2627.json'
import indoor2526OverridesJson from '@rules/pd-overrides/zaal2526__zaal2627.json'
import indoor2627DefinitionJson from '@rules/pd/zaal2627__zaal2728.json'
import indoor2627OverridesJson from '@rules/pd-overrides/zaal2627__zaal2728.json'
import outdoorDefinitionJson from '@rules/pd/veld2526vj__veld2627nj.json'
import outdoorOverridesJson from '@rules/pd-overrides/veld2526vj__veld2627nj.json'

export interface TransitionGroup {
  id: string
  poulePattern: string
  expectedPoules: number
}

export interface DrawDefinition {
  id: string
  groupId: string
  position: number
  shuffle?: boolean
}

export interface TransitionRule {
  id: string
  type: string
  groupId?: string
  positions?: number[]
  fromPosition?: number
  toPosition?: number
  position?: number
  selection?: string
  count?: number
  allowedPouleSizes?: number[]
  onlyUnassigned?: boolean
  tier?: string
}

export interface VacancyChainStep {
  groupId: string
  position: number
  allowedPouleSizes?: number[]
}

export interface VacancyChainDefinition {
  targetTier: string
  steps: VacancyChainStep[]
}

export interface SimpleVacancyChainDefinition {
  targetTiers: string[]
  sameTierPositions: number[]
  lowerTierPositions: number[]
}

export interface TeamSelector {
  groupId?: string
  pouleName?: string
  position?: number
  drawId?: string
  slot?: number
  winnerOf?: string
  loserOf?: string
}

export interface PlayoffStage {
  id: string
  type: string
  teamA: TeamSelector
  teamB: TeamSelector
  dates?: string[]
  date?: string
  neutral?: boolean
}

export interface PlayoffPlacement {
  selector: TeamSelector
  tier?: string
  event?: string
}

export interface PlayoffDefinition {
  id: string
  stages: PlayoffStage[]
  placements: PlayoffPlacement[]
}

export interface SeasonTransitionDefinition {
  id: string
  groups: TransitionGroup[]
  tierOrder?: string[]
  baselineCountOverrides?: Record<string, number>
  draws?: DrawDefinition[]
  vacancyChains?: VacancyChainDefinition[]
  simpleVacancyChain?: SimpleVacancyChainDefinition
  rules: TransitionRule[]
  playoffs?: PlayoffDefinition[]
}

export interface TransitionOverrideAction {
  type: string
  team?: string
  targetTier?: string
  reason?: string
}

export interface SeasonTransitionOverrides {
  actions: TransitionOverrideAction[]
}

export interface RelevantTeamsScope {
  tierName: string
  pouleName: string
  relevantPoules: string[]
  relevantTeams: string[]
  sourceGroupId: string
}

interface LoadedSeasonTransition {
  definition: SeasonTransitionDefinition
  overrides: SeasonTransitionOverrides
}

interface TiersData {
  [tierName: string]: {
    [pouleName: string]: string[]
  }
}

interface BasePlayoffSource {
  groupId: string
  position: number
  pouleName?: string
}

const transitionsBySeason: Record<KnownSeasonName, LoadedSeasonTransition> = {
  zaal2526: {
    definition: indoor2526DefinitionJson as SeasonTransitionDefinition,
    overrides: indoor2526OverridesJson as SeasonTransitionOverrides,
  },
  zaal2627: {
    definition: indoor2627DefinitionJson as SeasonTransitionDefinition,
    overrides: indoor2627OverridesJson as SeasonTransitionOverrides,
  },
  veld2526vj: {
    definition: outdoorDefinitionJson as SeasonTransitionDefinition,
    overrides: outdoorOverridesJson as SeasonTransitionOverrides,
  },
}

function ruleMatchesPouleSize(rule: TransitionRule, pouleSize: number): boolean {
  return !rule.allowedPouleSizes || rule.allowedPouleSizes.includes(pouleSize)
}

export function selectedIndices(rule: TransitionRule, size: number): number[] {
  const explicitPositions = (rule.positions ?? [])
    .map((position) => position - 1)
    .filter((index) => index >= 0 && index < size)
  const rangedPositions = rule.fromPosition == null
    ? []
    : (() => {
      const start = rule.fromPosition - 1
      const end = Math.min((rule.toPosition ?? size) - 1, size - 1)
      if (start < 0 || start > end) {
        return []
      }
      return Array.from({ length: end - start + 1 }, (_, index) => start + index)
    })()
  return [...new Set([...explicitPositions, ...rangedPositions])].sort((a, b) => a - b)
}

export function findBasePlayoffSources(
  playoff: PlayoffDefinition,
  definition: SeasonTransitionDefinition,
): BasePlayoffSource[] {
  const drawsById = new Map((definition.draws ?? []).map((draw) => [draw.id, draw]))
  const sources: BasePlayoffSource[] = []
  for (const stage of playoff.stages) {
    for (const selector of [stage.teamA, stage.teamB]) {
      if (selector.groupId && selector.position) {
        sources.push({
          groupId: selector.groupId,
          position: selector.position,
          pouleName: selector.pouleName,
        })
      } else if (selector.drawId) {
        const draw = drawsById.get(selector.drawId)
        if (draw) {
          sources.push({
            groupId: draw.groupId,
            position: draw.position,
          })
        }
      }
    }
  }
  return sources
}

export function resolveVacancyChains(definition: SeasonTransitionDefinition): VacancyChainDefinition[] {
  if (definition.vacancyChains?.length) {
    return definition.vacancyChains
  }
  if (!definition.simpleVacancyChain) {
    return []
  }
  const simple = definition.simpleVacancyChain
  return simple.targetTiers.slice(0, -1).map((targetTier, index) => {
    const lowerTier = simple.targetTiers[index + 1]!
    const steps: VacancyChainStep[] = []
    const n = Math.max(simple.sameTierPositions.length, simple.lowerTierPositions.length)
    for (let i = 0; i < n; i++) {
      const sameTierPosition = simple.sameTierPositions[i]
      if (sameTierPosition != null) {
        steps.push({ groupId: targetTier, position: sameTierPosition })
      }
      const lowerTierPosition = simple.lowerTierPositions[i]
      if (lowerTierPosition != null) {
        steps.push({ groupId: lowerTier, position: lowerTierPosition })
      }
    }
    return {
      targetTier,
      steps,
    }
  })
}

function collectPossibleTargetTiers(
  definition: SeasonTransitionDefinition,
  sourceGroupId: string,
): Set<string> {
  const tiers = new Set<string>()
  for (const rule of definition.rules) {
    if (rule.groupId === sourceGroupId && rule.tier) {
      tiers.add(rule.tier)
    }
  }
  for (const playoff of definition.playoffs ?? []) {
    const sources = findBasePlayoffSources(playoff, definition)
    if (!sources.some((source) => source.groupId === sourceGroupId)) {
      continue
    }
    for (const placement of playoff.placements) {
      if (placement.tier) {
        tiers.add(placement.tier)
      }
    }
  }
  return tiers
}

function collectRelevantGroupIds(
  definition: SeasonTransitionDefinition,
  sourceGroupId: string,
): Set<string> {
  const groupIds = new Set<string>([sourceGroupId])
  const possibleTargetTiers = collectPossibleTargetTiers(definition, sourceGroupId)

  for (const playoff of definition.playoffs ?? []) {
    const sources = findBasePlayoffSources(playoff, definition)
    if (!sources.some((source) => source.groupId === sourceGroupId)) {
      continue
    }
    for (const source of sources) {
      groupIds.add(source.groupId)
    }
  }

  for (const chain of resolveVacancyChains(definition)) {
    if (!possibleTargetTiers.has(chain.targetTier)) {
      continue
    }
    groupIds.add(chain.targetTier)
    for (const step of chain.steps) {
      groupIds.add(step.groupId)
    }
  }

  return groupIds
}

export function getSeasonTransition(season: KnownSeasonName): LoadedSeasonTransition {
  const transition = transitionsBySeason[season]
  if (!transition) {
    throw new Error(`No frontend transition definition for ${season}`)
  }
  return transition
}

export function getSeasonTransitionOrNull(season: string): LoadedSeasonTransition | null {
  return Object.prototype.hasOwnProperty.call(transitionsBySeason, season)
    ? (transitionsBySeason[season as KnownSeasonName] ?? null)
    : null
}

export function getWithdrawalReason(season: string, teamName: string): string | null {
  return getSeasonTransitionOrNull(season)?.overrides.actions.find((action) =>
    action.type === 'withdraw' && action.team === teamName,
  )?.reason ?? null
}

export function getWithdrawalsForTeams(season: string, teamNames: string[]): Array<{ teamName: string, reason: string }> {
  return teamNames.flatMap((teamName) => {
    const reason = getWithdrawalReason(season, teamName)
    return reason ? [{ teamName, reason }] : []
  })
}

export function findGroupForPoule(
  definition: SeasonTransitionDefinition,
  pouleName: string,
): TransitionGroup {
  const group = definition.groups.find(({ poulePattern }) => new RegExp(poulePattern).test(pouleName))
  if (!group) {
    throw new Error(`No transition group for poule ${pouleName} in ${definition.id}`)
  }
  return group
}

export function tierRank(
  definition: SeasonTransitionDefinition,
  tier: string,
): number {
  if (!definition.tierOrder) {
    throw new Error(`tierOrder missing for ${definition.id}`)
  }
  const rank = definition.tierOrder.indexOf(tier)
  if (rank < 0) {
    throw new Error(`Unknown tier ${tier} for ${definition.id}`)
  }
  return rank
}

export function championshipPlayoffQualification(
  definition: SeasonTransitionDefinition,
  sourceGroupId: string,
  pouleName: string,
  position: number,
): boolean {
  return (definition.playoffs ?? []).some((playoff) => {
    if (!playoff.placements.some((placement) => placement.event === 'champion')) {
      return false
    }
    return findBasePlayoffSources(playoff, definition).some((source) =>
      source.groupId === sourceGroupId
      && source.position === position
      && (!source.pouleName || source.pouleName === pouleName),
    )
  })
}

export function automaticChampionApplies(
  definition: SeasonTransitionDefinition,
  sourceGroupId: string,
  pouleName: string,
  position: number,
): boolean {
  return position === 1 && !championshipPlayoffQualification(definition, sourceGroupId, pouleName, position)
}

function treatTransitionAsSameTierOutcome(
  definition: SeasonTransitionDefinition,
  sourceGroupId: string,
  targetTier: string,
): boolean {
  return definition.id === 'veld2526vj__veld2627nj'
    && sourceGroupId === 'ekd'
    && targetTier === 'ek'
}

export function seasonOutcomePromotionApplies(
  definition: SeasonTransitionDefinition,
  sourceGroupId: string,
  pouleName: string,
  position: number,
  targetTier: string | null,
): boolean {
  if (championshipPlayoffQualification(definition, sourceGroupId, pouleName, position)) {
    return true
  }
  if (!targetTier) {
    return false
  }
  const sourceRank = tierRank(definition, sourceGroupId)
  const targetRank = tierRank(definition, targetTier)
  return targetRank < sourceRank && !treatTransitionAsSameTierOutcome(definition, sourceGroupId, targetTier)
}

export function seasonOutcomeRelegationApplies(
  definition: SeasonTransitionDefinition,
  sourceGroupId: string,
  targetTier: string | null,
): boolean {
  if (!targetTier) {
    return false
  }
  return tierRank(definition, targetTier) > tierRank(definition, sourceGroupId)
}

export function relevantTeamsScopeForTeam(
  season: KnownSeasonName,
  tiers: TiersData,
  teamName: string,
): RelevantTeamsScope | null {
  const transition = getSeasonTransition(season)
  const tierEntry = Object.entries(tiers)
    .find(([, poules]) => Object.values(poules ?? {}).some((teams) => teams.includes(teamName)))
  if (!tierEntry) {
    return null
  }
  const [tierName, poules] = tierEntry
  const pouleEntry = Object.entries(poules)
    .find(([, teams]) => teams.includes(teamName))
  if (!pouleEntry) {
    return null
  }
  const [pouleName] = pouleEntry
  const sourceGroupId = findGroupForPoule(transition.definition, pouleName).id
  const relevantGroupIds = collectRelevantGroupIds(transition.definition, sourceGroupId)

  const relevantPoules: string[] = []
  const relevantTeams: string[] = []
  for (const seasonTierPoules of Object.values(tiers)) {
    for (const [candidatePouleName, teams] of Object.entries(seasonTierPoules)) {
      const candidateGroupId = findGroupForPoule(transition.definition, candidatePouleName).id
      if (!relevantGroupIds.has(candidateGroupId)) {
        continue
      }
      relevantPoules.push(candidatePouleName)
      relevantTeams.push(...teams)
    }
  }

  return {
    tierName,
    pouleName,
    relevantPoules,
    relevantTeams,
    sourceGroupId,
  }
}

export function teamNeedsExpandedEvaluation(
  season: KnownSeasonName,
  sourceGroupId: string,
  pouleName: string,
  position: number,
  pouleSize: number,
  teamName: string,
): boolean {
  const { definition, overrides } = getSeasonTransition(season)
  if (overrides.actions.some((action) => action.team === teamName)) {
    return true
  }

  const applicableRules = definition.rules.filter((rule) => {
    if (rule.groupId !== sourceGroupId) {
      return false
    }
    if (!ruleMatchesPouleSize(rule, pouleSize)) {
      return false
    }
    if (rule.type === 'ranked') {
      return rule.position === position
    }
    if (rule.type === 'direct') {
      return selectedIndices(rule, pouleSize).includes(position - 1)
    }
    return false
  })

  if (applicableRules.some((rule) => rule.type !== 'direct' || rule.onlyUnassigned)) {
    return true
  }

  if (applicableRules.some((rule) =>
    rule.type === 'direct'
    && rule.tier
    && vacancyChainCanImproveDirectOutcome(definition, sourceGroupId, position, pouleSize, rule.tier),
  )) {
    return true
  }

  return (definition.playoffs ?? []).some((playoff) =>
    findBasePlayoffSources(playoff, definition).some((source) =>
      source.groupId === sourceGroupId
      && source.position === position
      && (!source.pouleName || source.pouleName === pouleName),
    ),
  )
}

function vacancyChainCanImproveDirectOutcome(
  definition: SeasonTransitionDefinition,
  sourceGroupId: string,
  position: number,
  pouleSize: number,
  directTargetTier: string,
): boolean {
  const directTargetRank = tierRank(definition, directTargetTier)
  return resolveVacancyChains(definition).some((chain) =>
    tierRank(definition, chain.targetTier) < directTargetRank
    && chain.steps.some((step) =>
      step.groupId === sourceGroupId
      && step.position === position
      && (!step.allowedPouleSizes || step.allowedPouleSizes.includes(pouleSize)),
    ),
  )
}
