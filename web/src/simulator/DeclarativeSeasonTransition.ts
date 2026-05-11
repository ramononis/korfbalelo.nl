import type { KnownSeasonName } from '@/season'
import type { Team } from '@/types'
import type { StandingResult } from '@/simulator/PoulePredicter'
import {
  automaticChampionApplies,
  findBasePlayoffSources,
  findGroupForPoule,
  getSeasonTransition,
  resolveVacancyChains,
  seasonOutcomePromotionApplies,
  seasonOutcomeRelegationApplies,
  selectedIndices,
  teamNeedsExpandedEvaluation,
  tierRank,
  type PlayoffDefinition,
  type TeamSelector,
  type TransitionRule,
  type VacancyChainDefinition,
} from '@/simulator/SeasonTransitionRules'

export interface TransitionOutcome {
  champion: boolean
  promote: boolean
  relegate: boolean
}

interface MatchResolver {
  match: (teamA: Team, teamB: Team, date: string, neutral?: boolean) => [Team, Team]
  playOffSeries: (teamA: Team, teamB: Team, dates: string[]) => [Team, Team]
}

interface RankedStandingEntry {
  entry: [Team, [number, number, number]]
  factor: number
}

interface TeamStandingSource {
  groupId: string
  pouleName: string
  position: number
  entry: RankedStandingEntry
}

interface LoadedPouleStanding {
  pouleName: string
  standing: StandingResult
}

export function evaluateTransitionOutcome(
  season: KnownSeasonName,
  teamName: string,
  pouleName: string,
  standingsByPoule: Map<string, StandingResult>,
  { match, playOffSeries }: MatchResolver,
): TransitionOutcome {
  const { definition, overrides } = getSeasonTransition(season)
  const sourceGroup = findGroupForPoule(definition, pouleName).id
  const ownStanding = standingsByPoule.get(pouleName)
  if (!ownStanding) {
    throw new Error(`Missing standing for ${pouleName}`)
  }
  const ownPosition = ownStanding.findIndex(([team]) => team.name === teamName)
  if (ownPosition < 0) {
    throw new Error(`Missing ${teamName} in ${pouleName}`)
  }

  const ownPositionOneBased = ownPosition + 1
  const champion = automaticChampionApplies(definition, sourceGroup, pouleName, ownPositionOneBased)

  if (!teamNeedsExpandedEvaluation(season, sourceGroup, pouleName, ownPositionOneBased, ownStanding.length, teamName)) {
    const targetTier = resolveDirectTargetTier(definition.rules, sourceGroup, ownStanding.length, ownPositionOneBased)
    return {
      champion,
      promote: seasonOutcomePromotionApplies(definition, sourceGroup, pouleName, ownPositionOneBased, targetTier),
      relegate: seasonOutcomeRelegationApplies(definition, sourceGroup, targetTier),
    }
  }

  const result = simulateExpandedTransition(
    definition,
    overrides.actions,
    standingsByPoule,
    { match, playOffSeries },
  )

  const targetTier = result.tierByTeam.get(teamName) ?? sourceGroup
  return {
    champion: champion || (result.eventsByTeam.get(teamName)?.has('champion') ?? false),
    promote: seasonOutcomePromotionApplies(definition, sourceGroup, pouleName, ownPositionOneBased, targetTier),
    relegate: seasonOutcomeRelegationApplies(definition, sourceGroup, targetTier),
  }
}

function resolveDirectTargetTier(
  rules: TransitionRule[],
  sourceGroupId: string,
  pouleSize: number,
  position: number,
): string | null {
  const matching = rules.filter((rule) =>
    rule.groupId === sourceGroupId
    && rule.type === 'direct'
    && (!rule.allowedPouleSizes || rule.allowedPouleSizes.includes(pouleSize))
    && selectedIndices(rule, pouleSize).includes(position - 1),
  )
  if (!matching.length) {
    return null
  }
  return matching[0]?.tier ?? null
}

function simulateExpandedTransition(
  definition: ReturnType<typeof getSeasonTransition>['definition'],
  overrideActions: ReturnType<typeof getSeasonTransition>['overrides']['actions'],
  standingsByPoule: Map<string, StandingResult>,
  { match, playOffSeries }: MatchResolver,
): {
  tierByTeam: Map<string, string>
  eventsByTeam: Map<string, Set<string>>
} {
  const groups = new Map<string, LoadedPouleStanding[]>()
  for (const group of definition.groups) {
    const poules = [...standingsByPoule.entries()]
      .filter(([name]) => new RegExp(group.poulePattern).test(name))
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([resolvedPouleName, standing]) => ({ pouleName: resolvedPouleName, standing }))
    groups.set(group.id, poules)
  }

  const tierByTeam = new Map<string, string>()
  const eventsByTeam = new Map<string, Set<string>>()
  const sourceByTeam = new Map<string, TeamStandingSource>()
  for (const [groupId, poules] of groups) {
    for (const poule of poules) {
      poule.standing.forEach((entry, index) => {
        sourceByTeam.set(entry[0].name, {
          groupId,
          pouleName: poule.pouleName,
          position: index + 1,
          entry: { entry, factor: poule.standing.length - 1 },
        })
      })
    }
  }

  const assign = (team: Team, tier: string, source: string) => {
    const previous = tierByTeam.get(team.name)
    if (previous) {
      throw new Error(`Team ${team.name} already assigned to ${previous} while applying ${source}`)
    }
    tierByTeam.set(team.name, tier)
  }
  const event = (team: Team, name: string) => {
    if (!eventsByTeam.has(team.name)) {
      eventsByTeam.set(team.name, new Set())
    }
    eventsByTeam.get(team.name)!.add(name)
  }

  for (const rule of definition.rules) {
    if (rule.type === 'direct') {
      applyDirectRule(rule, groups, tierByTeam, (team, tier) => assign(team, tier, rule.id))
    } else if (rule.type === 'ranked') {
      applyRankedRule(rule, groups, tierByTeam, match, (team, tier) => assign(team, tier, rule.id))
    } else {
      throw new Error(`Unsupported rule type ${rule.type}`)
    }
  }

  const draws = new Map<string, Team[]>()
  for (const draw of definition.draws ?? []) {
    const teams = (groups.get(draw.groupId) ?? [])
      .map(({ standing }) => standing[draw.position - 1]?.[0])
      .filter((team): team is Team => Boolean(team))
    draws.set(draw.id, draw.shuffle ? shuffled(teams) : teams)
  }

  for (const playoff of definition.playoffs ?? []) {
    const sources = findBasePlayoffSources(playoff, definition)
    if (sources.some((source) => (groups.get(source.groupId) ?? []).length === 0)) {
      continue
    }
    applyPlayoff(playoff, groups, draws, assign, event, match, playOffSeries)
  }

  applyOverridesAndVacancyChains(
    definition,
    overrideActions,
    tierByTeam,
    groups,
    sourceByTeam,
    match,
  )

  return {
    tierByTeam,
    eventsByTeam,
  }
}

function applyDirectRule(
  rule: TransitionRule,
  groups: Map<string, LoadedPouleStanding[]>,
  tierByTeam: Map<string, string>,
  assign: (team: Team, tier: string) => void,
) {
  const poules = (groups.get(rule.groupId!) ?? [])
    .filter(({ standing }) => !rule.allowedPouleSizes || rule.allowedPouleSizes.includes(standing.length))
  for (const poule of poules) {
    const result = poule.standing.map(([team]) => team)
    for (const index of selectedIndices(rule, result.length)) {
      const team = result[index]
      if (!team) {
        continue
      }
      if (!rule.onlyUnassigned || !tierByTeam.has(team.name)) {
        assign(team, rule.tier!)
      }
    }
  }
}

function applyRankedRule(
  rule: TransitionRule,
  groups: Map<string, LoadedPouleStanding[]>,
  tierByTeam: Map<string, string>,
  match: MatchResolver['match'],
  assign: (team: Team, tier: string) => void,
) {
  const position = (rule.position ?? 1) - 1
  const entries = (groups.get(rule.groupId!) ?? [])
    .filter(({ standing }) => standing.length > position)
    .filter(({ standing }) => !rule.allowedPouleSizes || rule.allowedPouleSizes.includes(standing.length))
    .map(({ standing }) => ({ entry: standing[position]!, factor: standing.length - 1 }))
    .filter((candidate) => !rule.onlyUnassigned || !tierByTeam.has(candidate.entry[0].name))
    .sort((left, right) => compareStandingEntries(left, right, match))
  const count = rule.count ?? 0
  const selected = rule.selection === 'best'
    ? entries.slice(0, count)
    : (rule.onlyUnassigned ? entries : entries.slice(count))
  for (const entry of selected) {
    assign(entry.entry[0], rule.tier!)
  }
}

function applyPlayoff(
  playoff: PlayoffDefinition,
  groups: Map<string, LoadedPouleStanding[]>,
  draws: Map<string, Team[]>,
  assign: (team: Team, tier: string, source: string) => void,
  event: (team: Team, name: string) => void,
  match: MatchResolver['match'],
  playOffSeries: MatchResolver['playOffSeries'],
) {
  const outcomes = new Map<string, [Team, Team]>()
  for (const stage of playoff.stages) {
    const teamA = resolveSelector(stage.teamA, groups, draws, outcomes)
    const teamB = resolveSelector(stage.teamB, groups, draws, outcomes)
    const outcome = stage.type === 'best_of_3'
      ? playOffSeries(teamA, teamB, stage.dates ?? [])
      : match(teamA, teamB, stage.date!, stage.neutral ?? false)
    outcomes.set(stage.id, outcome)
  }
  for (const placement of playoff.placements) {
    const team = resolveSelector(placement.selector, groups, draws, outcomes)
    if (placement.tier) {
      assign(team, placement.tier, `${playoff.id}:${placement.selector}`)
    }
    if (placement.event) {
      event(team, placement.event)
    }
  }
}

function applyOverridesAndVacancyChains(
  definition: ReturnType<typeof getSeasonTransition>['definition'],
  overrideActions: ReturnType<typeof getSeasonTransition>['overrides']['actions'],
  tierByTeam: Map<string, string>,
  groups: Map<string, LoadedPouleStanding[]>,
  sourceByTeam: Map<string, TeamStandingSource>,
  match: MatchResolver['match'],
) {
  const chains = resolveVacancyChains(definition)
  if (!overrideActions.length && !chains.length) {
    return
  }

  const baselineCounts = new Map<string, number>()
  for (const tier of tierByTeam.values()) {
    baselineCounts.set(tier, (baselineCounts.get(tier) ?? 0) + 1)
  }
  for (const [tier, expectedCount] of Object.entries(definition.baselineCountOverrides ?? {})) {
    baselineCounts.set(tier, expectedCount)
  }

  for (const action of overrideActions) {
    if (action.type === 'withdraw') {
      applyWithdraw(definition, action.team!, tierByTeam, groups, sourceByTeam)
    } else if (action.type === 'appear') {
      if (action.team && action.targetTier) {
        tierByTeam.set(action.team, action.targetTier)
      }
    } else if (action.type === 'declinePromotion') {
      applyDeclinePromotion(definition, action.team!, action.targetTier!, tierByTeam, groups, sourceByTeam)
    } else {
      throw new Error(`Unsupported override action ${action.type}`)
    }
  }

  const chainsByTier = new Map(chains.map((chain) => [chain.targetTier, chain]))
  while (true) {
    const nextVacancyTier = [...baselineCounts.entries()].find(([tier, expectedCount]) =>
      chainsByTier.has(tier) && [...tierByTeam.values()].filter((value) => value === tier).length < expectedCount,
    )?.[0]
    if (!nextVacancyTier) {
      break
    }
    const candidate = findVacancyCandidate(definition, chainsByTier.get(nextVacancyTier)!, nextVacancyTier, tierByTeam, groups, match)
    if (!candidate) {
      throw new Error(`Unable to resolve vacancy for ${nextVacancyTier}`)
    }
    tierByTeam.set(candidate.entry.entry[0].name, nextVacancyTier)
  }
}

function applyWithdraw(
  definition: ReturnType<typeof getSeasonTransition>['definition'],
  teamName: string,
  tierByTeam: Map<string, string>,
  groups: Map<string, LoadedPouleStanding[]>,
  sourceByTeam: Map<string, TeamStandingSource>,
) {
  const currentTier = tierByTeam.get(teamName)
  if (!currentTier) {
    return
  }
  const source = sourceByTeam.get(teamName)
  if (source && source.position === 1 && tierRank(definition, currentTier) < tierRank(definition, source.groupId)) {
    const runnerUp = findRunnerUp(source, groups)
    if (tierByTeam.get(runnerUp.name) !== currentTier) {
      tierByTeam.set(runnerUp.name, currentTier)
    }
  }
  tierByTeam.delete(teamName)
}

function applyDeclinePromotion(
  definition: ReturnType<typeof getSeasonTransition>['definition'],
  teamName: string,
  requestedTier: string,
  tierByTeam: Map<string, string>,
  groups: Map<string, LoadedPouleStanding[]>,
  sourceByTeam: Map<string, TeamStandingSource>,
) {
  const currentTier = tierByTeam.get(teamName)
  if (!currentTier) {
    return
  }
  if (tierRank(definition, requestedTier) <= tierRank(definition, currentTier)) {
    return
  }
  const source = sourceByTeam.get(teamName)
  if (!source || source.position !== 1) {
    throw new Error(`Only poule-winner declines are supported for ${teamName}`)
  }
  tierByTeam.set(teamName, requestedTier)
  const runnerUp = findRunnerUp(source, groups)
  if (tierByTeam.get(runnerUp.name) !== currentTier) {
    tierByTeam.set(runnerUp.name, currentTier)
  }
}

function findRunnerUp(
  source: TeamStandingSource,
  groups: Map<string, LoadedPouleStanding[]>,
): Team {
  const poule = (groups.get(source.groupId) ?? []).find(({ pouleName }) => pouleName === source.pouleName)
  const runnerUp = poule?.standing[1]?.[0]
  if (!runnerUp) {
    throw new Error(`No runner-up available in ${source.pouleName}`)
  }
  return runnerUp
}

function findVacancyCandidate(
  definition: ReturnType<typeof getSeasonTransition>['definition'],
  chain: VacancyChainDefinition,
  targetTier: string,
  tierByTeam: Map<string, string>,
  groups: Map<string, LoadedPouleStanding[]>,
  match: MatchResolver['match'],
): TeamStandingSource | null {
  const targetRank = tierRank(definition, targetTier)
  for (const step of chain.steps) {
    const candidates = (groups.get(step.groupId) ?? [])
      .filter(({ standing }) => !step.allowedPouleSizes || step.allowedPouleSizes.includes(standing.length))
      .filter(({ standing }) => standing.length >= step.position)
      .map(({ pouleName, standing }) => ({
        groupId: step.groupId,
        pouleName,
        position: step.position,
        entry: {
          entry: standing[step.position - 1]!,
          factor: standing.length - 1,
        },
      }))
      .filter((candidate) => {
        const currentTier = tierByTeam.get(candidate.entry.entry[0].name)
        return currentTier != null && tierRank(definition, currentTier) > targetRank
      })
      .sort((left, right) => compareStandingEntries(left.entry, right.entry, match))
    if (candidates.length) {
      return candidates[0]!
    }
  }
  return null
}

function resolveSelector(
  selector: TeamSelector,
  groups: Map<string, LoadedPouleStanding[]>,
  draws: Map<string, Team[]>,
  outcomes: Map<string, [Team, Team]>,
): Team {
  if (selector.winnerOf) {
    return outcomes.get(selector.winnerOf)![0]
  }
  if (selector.loserOf) {
    return outcomes.get(selector.loserOf)![1]
  }
  if (selector.drawId) {
    return draws.get(selector.drawId)![selector.slot! - 1]!
  }
  const poules = groups.get(selector.groupId!) ?? []
  const poule = selector.pouleName
    ? poules.find(({ pouleName }) => pouleName === selector.pouleName)
    : poules[0]
  const team = poule?.standing[selector.position! - 1]?.[0]
  if (!team) {
    throw new Error(`Unable to resolve selector ${JSON.stringify(selector)}`)
  }
  return team
}

function compareStandingEntries(
  left: RankedStandingEntry,
  right: RankedStandingEntry,
  match: MatchResolver['match'],
): number {
  const [leftPoints, leftDiff, leftGoals] = left.entry[1]
  const [rightPoints, rightDiff, rightGoals] = right.entry[1]
  if (leftPoints * right.factor !== rightPoints * left.factor) {
    return rightPoints * left.factor - leftPoints * right.factor
  }
  if (leftDiff * right.factor !== rightDiff * left.factor) {
    return rightDiff * left.factor - leftDiff * right.factor
  }
  if (leftGoals * right.factor !== rightGoals * left.factor) {
    return rightGoals * left.factor - leftGoals * right.factor
  }
  return match(left.entry[0], right.entry[0], '2099-01-01', true)[0] === left.entry[0] ? -1 : 1
}

function shuffled<T>(items: T[]): T[] {
  const copy = [...items]
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[copy[i], copy[j]] = [copy[j]!, copy[i]!]
  }
  return copy
}
