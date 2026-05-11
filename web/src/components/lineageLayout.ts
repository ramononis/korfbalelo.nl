import type { DiscontinuedTeamRow } from '@/stores/discontinuedTeams'

export type LineageEdgeType = 'rename' | 'merge_into' | 'fuse_new'
export type LineageEventType = LineageEdgeType | 'spawn' | 'end'
export type LineageSegmentKind = 'normal' | 'terminal_end' | 'terminal_continue'
export type MergeCurveStyle = 'smooth' | 'elbow' | 'arc'

interface LineageEdge {
  id: string
  source: string
  target: string
  date: Date
  type: LineageEdgeType
}

export interface LineageEvent {
  id: string
  date: Date
  target: string
  type: LineageEventType
  sources: string[]
}

export interface LineageSegment {
  id: string
  team: string
  fromRow: number
  toRow: number
  fromLane: number
  toLane: number
  kind: LineageSegmentKind
  mergeCurveStyle?: MergeCurveStyle
}

export interface LineageNode {
  id: string
  lane: number
  row: number
  event: LineageEvent
}

export interface LineageLayoutData {
  laneCount: number
  rowCount: number
  segments: LineageSegment[]
  nodes: LineageNode[]
  eventsById: Map<string, LineageEvent>
  currentLane: number
  showCurrentNode: boolean
  rowLabels: Array<{ row: number, date: Date }>
  rowYByRow: number[]
  innerHeight: number
}

interface ActiveLine {
  lane: number
  lastRow: number
}

interface SourceEntry extends ActiveLine {
  team: string
}

export interface BuildLineageLayoutInput {
  teamName: string
  discontinuedRows: DiscontinuedTeamRow[]
  teamStartDate?: Date | null
  nowEpoch?: number
}

const DAY_MS = 24 * 60 * 60 * 1000
const YEAR_MS = 365.2425 * DAY_MS
const TIMELINE_PIXELS_PER_YEAR = 14
const DATE_LABEL_LINE_HEIGHT = 12
const MIN_EVENT_GAP = DATE_LABEL_LINE_HEIGHT * 2.925
const MAX_TIMELINE_HEIGHT = 960 * 0.7
const LANE_GAP = 86
const PADDING_LEFT = 112
const PADDING_TOP = 20

export function buildLineageLayout(input: BuildLineageLayoutInput): LineageLayoutData | null {
  const { teamName, discontinuedRows, teamStartDate } = input
  const rowByName = new Map(discontinuedRows.map((row) => [row.name, row]))
  const firstKnownStartDate = (team: string): Date | null => {
    if (team === teamName) {
      return teamStartDate ?? rowByName.get(team)?.firstMatchDate ?? null
    }
    return rowByName.get(team)?.firstMatchDate ?? null
  }

  const terminalEvent = buildTerminalEvent(teamName, rowByName.get(teamName))
  const edges = collectOriginEdges(teamName, discontinuedRows)
  const events = groupEvents(edges)
  if (events.length === 0 && !terminalEvent) {
    return null
  }

  const sourceTeams = new Set<string>()
  const targetTeams = new Set<string>()
  events.forEach((event) => {
    targetTeams.add(event.target)
    event.sources.forEach((source) => sourceTeams.add(source))
  })

  const roots = Array.from(sourceTeams).filter((name) => !targetTeams.has(name))
  const firstEventByTeam = new Map<string, number>()
  for (const event of events) {
    for (const source of event.sources) {
      const current = firstEventByTeam.get(source)
      const timestamp = event.date.getTime()
      if (current == null || timestamp < current) {
        firstEventByTeam.set(source, timestamp)
      }
    }
  }
  roots.sort((a, b) => {
    const aTime = firstEventByTeam.get(a) ?? Number.MAX_SAFE_INTEGER
    const bTime = firstEventByTeam.get(b) ?? Number.MAX_SAFE_INTEGER
    if (aTime !== bTime) {
      return aTime - bTime
    }
    return a.localeCompare(b)
  })

  const segments: LineageSegment[] = []
  const nodes: LineageNode[] = []
  const eventsById = new Map<string, LineageEvent>()
  const active = new Map<string, ActiveLine>()
  let nextLane = 0

  const reusableLaneFreeAfterRow = new Map<number, number>()
  const allocateLane = (startRow: number): number => {
    for (const [lane, freeAfterRow] of Array.from(reusableLaneFreeAfterRow.entries()).sort((a, b) => a[0] - b[0])) {
      if (freeAfterRow < startRow) {
        reusableLaneFreeAfterRow.delete(lane)
        return lane
      }
    }
    return nextLane++
  }
  const releaseLane = (lane: number, row: number) => {
    reusableLaneFreeAfterRow.set(lane, row)
  }

  const isLaneStrictlyBetween = (lane: number, a: number, b: number): boolean => {
    return (a < lane && lane < b) || (b < lane && lane < a)
  }
  const laneAtRow = (fromLane: number, toLane: number, fromRow: number, toRow: number, row: number): number => {
    const span = toRow - fromRow
    if (span === 0) {
      return toLane
    }
    const t = (row - fromRow) / span
    return fromLane + (toLane - fromLane) * t
  }
  const historyCrossingsForCandidate = (
    source: SourceEntry,
    candidateLane: number,
    eventRow: number,
    historySegments: LineageSegment[],
  ): number => {
    if (source.lastRow >= eventRow) {
      return 0
    }
    let crossings = 0
    for (const history of historySegments) {
      if (history.team === source.team) {
        continue
      }
      const overlapStart = Math.max(source.lastRow, history.fromRow)
      const overlapEnd = Math.min(eventRow, history.toRow)
      if (overlapEnd <= overlapStart) {
        continue
      }
      const epsilon = 1e-3
      const firstRow = overlapStart + epsilon
      const lastRow = overlapEnd - epsilon
      const sampleA = firstRow < lastRow ? firstRow : overlapStart + (overlapEnd - overlapStart) * 0.5
      const sampleB = firstRow < lastRow ? lastRow : sampleA
      const d0 =
        laneAtRow(source.lane, candidateLane, source.lastRow, eventRow, sampleA)
        - laneAtRow(history.fromLane, history.toLane, history.fromRow, history.toRow, sampleA)
      const d1 =
        laneAtRow(source.lane, candidateLane, source.lastRow, eventRow, sampleB)
        - laneAtRow(history.fromLane, history.toLane, history.fromRow, history.toRow, sampleB)
      if (d0 * d1 < 0) {
        crossings++
        continue
      }
      // Near-touching lines are treated as crossing-risk for visual clarity.
      if (Math.abs(d0) < 0.04 || Math.abs(d1) < 0.04) {
        crossings++
      }
    }
    return crossings
  }
  const chooseTargetLane = (
    existingTarget: ActiveLine | undefined,
    sourceEntries: SourceEntry[],
    keepLanes: number[],
    eventRow: number,
    historySegments: LineageSegment[],
  ): number => {
    if (existingTarget) {
      return existingTarget.lane
    }
    const sourceLanes = sourceEntries.map((source) => source.lane)
    if (sourceLanes.length === 0) {
      return 0
    }
    const uniqueCandidates = Array.from(new Set(sourceLanes)).sort((a, b) => a - b)
    const mid = Math.floor(sourceLanes.length / 2)
    const medianLane =
      sourceLanes.length % 2 === 1
        ? (sourceLanes[mid] ?? uniqueCandidates[0] ?? 0)
        : (((sourceLanes[mid - 1] ?? sourceLanes[mid] ?? 0) + (sourceLanes[mid] ?? sourceLanes[mid - 1] ?? 0)) / 2)

    let bestLane = uniqueCandidates[0] ?? medianLane
    let bestScore = Number.POSITIVE_INFINITY
    const nearestKeepLaneDistance = (candidate: number): number => {
      if (keepLanes.length === 0) {
        return Number.POSITIVE_INFINITY
      }
      return keepLanes.reduce((best, lane) => Math.min(best, Math.abs(lane - candidate)), Number.POSITIVE_INFINITY)
    }
    for (const candidate of uniqueCandidates) {
      let crossings = 0
      let distanceSum = 0
      let historyCrossings = 0
      for (const source of sourceEntries) {
        const sourceLane = source.lane
        distanceSum += Math.abs(sourceLane - candidate)
        historyCrossings += historyCrossingsForCandidate(source, candidate, eventRow, historySegments)
        if (sourceLane === candidate) {
          continue
        }
        for (const keepLane of keepLanes) {
          if (isLaneStrictlyBetween(keepLane, sourceLane, candidate)) {
            crossings++
          }
        }
      }
      const score = historyCrossings * 100000 + crossings * 1000 + distanceSum
      if (score < bestScore) {
        bestScore = score
        bestLane = candidate
        continue
      }
      if (score === bestScore) {
        const bestKeepDistance = nearestKeepLaneDistance(bestLane)
        const candidateKeepDistance = nearestKeepLaneDistance(candidate)
        if (candidateKeepDistance > bestKeepDistance) {
          bestLane = candidate
          continue
        }
        if (candidateKeepDistance < bestKeepDistance) {
          continue
        }
        const bestMedianDistance = Math.abs(bestLane - medianLane)
        const candidateMedianDistance = Math.abs(candidate - medianLane)
        if (candidateMedianDistance < bestMedianDistance || (candidateMedianDistance === bestMedianDistance && candidate < bestLane)) {
          bestLane = candidate
        }
      }
    }
    return bestLane
  }

  const seededRoots = roots.length > 0 ? roots : dedupe(events.flatMap((event) => event.sources))
  const fallbackSpawnDate = events[0]?.date ?? terminalEvent?.date ?? new Date(0)
  const spawnDateByTeam = new Map<string, Date>()
  const rememberSpawnDate = (team: string, date: Date) => {
    const current = spawnDateByTeam.get(team)
    if (!current || date.getTime() < current.getTime()) {
      spawnDateByTeam.set(team, date)
    }
  }
  for (const team of seededRoots) {
    rememberSpawnDate(team, firstKnownStartDate(team) ?? fallbackSpawnDate)
  }
  for (const event of events) {
    for (const source of event.sources) {
      rememberSpawnDate(source, firstKnownStartDate(source) ?? event.date)
    }
    if (event.type === 'merge_into') {
      const targetStart = firstKnownStartDate(event.target) ?? event.date
      const boundedTargetStart = targetStart.getTime() <= event.date.getTime() ? targetStart : event.date
      rememberSpawnDate(event.target, boundedTargetStart)
    }
  }
  if (terminalEvent) {
    rememberSpawnDate(teamName, firstKnownStartDate(teamName) ?? terminalEvent.date)
  }

  const timelineEpochs = dedupeNumbers([
    ...events.map((event) => event.date.getTime()),
    ...Array.from(spawnDateByTeam.values()).map((date) => date.getTime()),
    ...(terminalEvent ? [terminalEvent.date.getTime()] : []),
  ]).sort((a, b) => a - b)
  const rowByEpoch = new Map<number, number>()
  timelineEpochs.forEach((epoch, row) => rowByEpoch.set(epoch, row))
  const rowForDate = (date: Date): number => {
    const epoch = date.getTime()
    const directRow = rowByEpoch.get(epoch)
    if (directRow != null) {
      return directRow
    }
    if (timelineEpochs.length === 0) {
      return 0
    }
    let low = 0
    let high = timelineEpochs.length - 1
    while (low <= high) {
      const mid = (low + high) >> 1
      const midEpoch = timelineEpochs[mid] ?? epoch
      if (midEpoch === epoch) {
        return mid
      }
      if (midEpoch < epoch) {
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    if (low <= 0) {
      return 0
    }
    if (low >= timelineEpochs.length) {
      return timelineEpochs.length - 1
    }
    const prevEpoch = timelineEpochs[low - 1] ?? epoch
    const nextEpoch = timelineEpochs[low] ?? epoch
    return epoch - prevEpoch <= nextEpoch - epoch ? low - 1 : low
  }

  const spawnNodeAdded = new Set<string>()
  const addSpawnNode = (team: string, lane: number, spawnDate: Date) => {
    if (spawnNodeAdded.has(team)) {
      return
    }
    spawnNodeAdded.add(team)
    const spawnEvent: LineageEvent = {
      id: `spawn|${team}`,
      date: spawnDate,
      target: team,
      type: 'spawn',
      sources: [],
    }
    const row = rowForDate(spawnDate)
    nodes.push({
      id: spawnEvent.id,
      lane,
      row,
      event: spawnEvent,
    })
    eventsById.set(spawnEvent.id, spawnEvent)
  }

  for (const event of events) {
    const row = rowForDate(event.date)
    const sourceSet = new Set(event.sources)

    if (event.type === 'merge_into' && !active.has(event.target)) {
      const targetStart = spawnDateByTeam.get(event.target) ?? firstKnownStartDate(event.target) ?? event.date
      const boundedTargetStart = targetStart.getTime() <= event.date.getTime() ? targetStart : event.date
      const spawnRow = Math.min(rowForDate(boundedTargetStart), row)
      const lane = allocateLane(spawnRow)
      active.set(event.target, { lane, lastRow: spawnRow })
      addSpawnNode(event.target, lane, boundedTargetStart)
    }

    for (const source of event.sources) {
      if (!active.has(source)) {
        const sourceStart = spawnDateByTeam.get(source) ?? firstKnownStartDate(source) ?? event.date
        const spawnDate = sourceStart.getTime() <= event.date.getTime() ? sourceStart : event.date
        const spawnRow = Math.min(rowForDate(spawnDate), row)
        const lane = allocateLane(spawnRow)
        active.set(source, { lane, lastRow: spawnRow })
        addSpawnNode(source, lane, spawnDate)
      }
    }

    const sourceEntries = event.sources
      .map((source) => {
        const state = active.get(source)
        if (!state) {
          return null
        }
        return { team: source, lane: state.lane, lastRow: state.lastRow }
      })
      .filter((line): line is SourceEntry => line != null)
    const activeSnapshot = Array.from(active.entries())
    const keepLanes = activeSnapshot
      .filter(([team]) => !sourceSet.has(team))
      .map(([, state]) => state.lane)
    const existingTarget = active.get(event.target)
    const targetLane = chooseTargetLane(existingTarget, sourceEntries, keepLanes, row, segments)

    for (const [team, state] of activeSnapshot) {
      if (sourceSet.has(team)) {
        if (state.lastRow <= row && (state.lastRow < row || state.lane !== targetLane)) {
          segments.push({
            id: `${team}|${state.lastRow}|${row}|merge`,
            team,
            fromRow: state.lastRow,
            toRow: row,
            fromLane: state.lane,
            toLane: targetLane,
            kind: 'normal',
            mergeCurveStyle: mergeCurveStyleForEvent(event),
          })
        }
      } else {
        if (state.lastRow < row) {
          segments.push({
            id: `${team}|${state.lastRow}|${row}|keep`,
            team,
            fromRow: state.lastRow,
            toRow: row,
            fromLane: state.lane,
            toLane: state.lane,
            kind: 'normal',
          })
        }
        const updatedRow = state.lastRow < row ? row : state.lastRow
        active.set(team, { lane: state.lane, lastRow: updatedRow })
      }
    }

    nodes.push({
      id: event.id,
      lane: targetLane,
      row,
      event,
    })
    eventsById.set(event.id, event)

    for (const source of event.sources) {
      const sourceState = active.get(source)
      if (!sourceState) {
        continue
      }
      active.delete(source)
      if (sourceState.lane !== targetLane) {
        releaseLane(sourceState.lane, row)
      }
    }
    active.set(event.target, { lane: targetLane, lastRow: row })
  }

  if (terminalEvent) {
    const terminalRow = rowForDate(terminalEvent.date)
    let terminalState = active.get(teamName)
    if (!terminalState) {
      const teamStart = spawnDateByTeam.get(teamName) ?? firstKnownStartDate(teamName) ?? terminalEvent.date
      const boundedStart = teamStart.getTime() <= terminalEvent.date.getTime() ? teamStart : terminalEvent.date
      const spawnRow = Math.min(rowForDate(boundedStart), terminalRow)
      const lane = allocateLane(spawnRow)
      terminalState = { lane, lastRow: spawnRow }
      active.set(teamName, terminalState)
      addSpawnNode(teamName, lane, boundedStart)
    }
    if (terminalState.lastRow < terminalRow) {
      segments.push({
        id: `${teamName}|${terminalState.lastRow}|${terminalRow}|terminal`,
        team: teamName,
        fromRow: terminalState.lastRow,
        toRow: terminalRow,
        fromLane: terminalState.lane,
        toLane: terminalState.lane,
        kind: terminalEvent.type === 'end' ? 'terminal_end' : 'terminal_continue',
      })
    }
    nodes.push({
      id: terminalEvent.id,
      lane: terminalState.lane,
      row: terminalRow,
      event: terminalEvent,
    })
    eventsById.set(terminalEvent.id, terminalEvent)
    active.delete(teamName)
  }

  const maxTimelineRow = timelineEpochs.length > 0 ? timelineEpochs.length - 1 : 0
  const showCurrentNode = !terminalEvent
  const finalRow = showCurrentNode ? maxTimelineRow + 1 : maxTimelineRow
  const rowCount = finalRow + 1
  if (showCurrentNode && active.size === 0) {
    active.set(teamName, { lane: 0, lastRow: finalRow })
    nextLane = Math.max(nextLane, 1)
  }
  for (const [team, state] of active) {
    if (state.lastRow < finalRow) {
      segments.push({
        id: `${team}|${state.lastRow}|${finalRow}|tail`,
        team,
        fromRow: state.lastRow,
        toRow: finalRow,
        fromLane: state.lane,
        toLane: state.lane,
        kind: 'normal',
      })
    }
  }

  const lastNodeLane = nodes.length > 0 ? nodes[nodes.length - 1]!.lane : 0
  let focusLane = active.get(teamName)?.lane
  if (focusLane == null) {
    for (let index = nodes.length - 1; index >= 0; index--) {
      const node = nodes[index]
      if (!node) {
        continue
      }
      if (node.event.target === teamName || node.event.sources.includes(teamName)) {
        focusLane = node.lane
        break
      }
    }
  }
  const currentLane = focusLane ?? lastNodeLane
  nextLane = Math.max(nextLane, currentLane + 1)

  const maxLaneSeen = Math.max(
    0,
    ...segments.flatMap((segment) => [segment.fromLane, segment.toLane]),
    ...nodes.map((node) => node.lane),
    currentLane,
  )
  const minEpoch = timelineEpochs[0] ?? fallbackSpawnDate.getTime()
  const maxEventEpoch = timelineEpochs[timelineEpochs.length - 1] ?? minEpoch
  const nowEpoch = input.nowEpoch ?? Date.now()
  const finalEpoch = Math.max(maxEventEpoch + DAY_MS, nowEpoch)
  const rowEpochByRow = showCurrentNode ? [...timelineEpochs, finalEpoch] : [...timelineEpochs]
  if (rowEpochByRow.length === 0) {
    rowEpochByRow.push(minEpoch)
  }
  const rowYByRow: number[] = [0]
  const rowGaps: number[] = []
  for (let index = 1; index < rowEpochByRow.length; index++) {
    const previousEpoch = rowEpochByRow[index - 1] ?? rowEpochByRow[index] ?? finalEpoch
    const currentEpoch = rowEpochByRow[index] ?? previousEpoch
    const durationMs = Math.max(0, currentEpoch - previousEpoch)
    const baseGap = (durationMs / YEAR_MS) * TIMELINE_PIXELS_PER_YEAR
    const gap = Math.max(MIN_EVENT_GAP, baseGap)
    rowGaps.push(gap)
    rowYByRow.push((rowYByRow[index - 1] ?? 0) + gap)
  }
  const timelineHeight = rowYByRow[rowYByRow.length - 1] ?? 0
  if (timelineHeight > MAX_TIMELINE_HEIGHT && rowGaps.length > 0) {
    const shrinkRatio = MAX_TIMELINE_HEIGHT / timelineHeight
    rowYByRow[0] = 0
    for (let index = 1; index < rowYByRow.length; index++) {
      rowYByRow[index] = (rowYByRow[index - 1] ?? 0) + (rowGaps[index - 1] ?? 0) * shrinkRatio
    }
  }
  const innerHeight = Math.max(rowYByRow[rowYByRow.length - 1] ?? 0, MIN_EVENT_GAP)

  return {
    laneCount: Math.max(maxLaneSeen + 1, 1),
    rowCount,
    segments,
    nodes,
    eventsById,
    currentLane,
    showCurrentNode,
    rowLabels: timelineEpochs.map((epoch, row) => ({ row, date: new Date(epoch) })),
    rowYByRow,
    innerHeight,
  }
}

function mergeCurveStyleForEvent(event: LineageEvent): MergeCurveStyle {
  if (event.type === 'rename') {
    return 'elbow'
  }
  if (event.type === 'merge_into') {
    return 'smooth'
  }
  return 'arc'
}

function resolveTarget(row: DiscontinuedTeamRow): string | null {
  if (row.fateType === 'rename' || row.fateType === 'merge_into') {
    return row.fateTeams[0] ?? null
  }
  if (row.fateType === 'fuse_new') {
    return row.fateTeams[row.fateTeams.length - 1] ?? null
  }
  return null
}

function buildTerminalEvent(teamName: string, row: DiscontinuedTeamRow | undefined): LineageEvent | null {
  if (!row) {
    return null
  }
  const dateKey = row.lastEventDate.toISOString().slice(0, 10)
  if (row.fateType === 'end') {
    return {
      id: `terminal|${teamName}|${dateKey}|end`,
      date: row.lastEventDate,
      target: teamName,
      type: 'end',
      sources: [teamName],
    }
  }
  const target = resolveTarget(row)
  if (!target) {
    return null
  }
  if (row.fateType === 'rename' || row.fateType === 'merge_into' || row.fateType === 'fuse_new') {
    return {
      id: `terminal|${teamName}|${dateKey}|${row.fateType}`,
      date: row.lastEventDate,
      target,
      type: row.fateType,
      sources: [teamName],
    }
  }
  return null
}

function collectOriginEdges(teamName: string, rows: DiscontinuedTeamRow[]): LineageEdge[] {
  const incoming = new Map<string, LineageEdge[]>()
  for (const row of rows) {
    const target = resolveTarget(row)
    if (!target) {
      continue
    }
    if (row.fateType !== 'rename' && row.fateType !== 'merge_into' && row.fateType !== 'fuse_new') {
      continue
    }
    const date = row.lastEventDate
    const edge: LineageEdge = {
      id: `${row.name}|${target}|${date.toISOString().slice(0, 10)}|${row.fateType}`,
      source: row.name,
      target,
      date,
      type: row.fateType as LineageEdgeType,
    }
    const list = incoming.get(target) ?? []
    list.push(edge)
    incoming.set(target, list)
  }

  const queue: string[] = [teamName]
  const visited = new Set<string>(queue)
  const selected = new Map<string, LineageEdge>()

  while (queue.length > 0) {
    const current = queue.pop()
    if (!current) {
      continue
    }
    for (const edge of incoming.get(current) ?? []) {
      selected.set(edge.id, edge)
      if (!visited.has(edge.source)) {
        visited.add(edge.source)
        queue.push(edge.source)
      }
    }
  }

  return Array.from(selected.values())
}

function groupEvents(edges: LineageEdge[]): LineageEvent[] {
  const groups = new Map<string, LineageEvent>()
  for (const edge of edges) {
    const dateKey = edge.date.toISOString().slice(0, 10)
    const key = `${dateKey}|${edge.target}|${edge.type}`
    const existing = groups.get(key)
    if (existing) {
      if (!existing.sources.includes(edge.source)) {
        existing.sources.push(edge.source)
      }
      continue
    }
    groups.set(key, {
      id: key,
      date: edge.date,
      target: edge.target,
      type: edge.type,
      sources: [edge.source],
    })
  }

  const result = Array.from(groups.values())
  result.forEach((event) => event.sources.sort((a, b) => a.localeCompare(b)))
  result.sort((a, b) => {
    const d = a.date.getTime() - b.date.getTime()
    if (d !== 0) {
      return d
    }
    const t = a.target.localeCompare(b.target)
    if (t !== 0) {
      return t
    }
    return a.type.localeCompare(b.type)
  })
  return result
}

function dedupe(values: string[]): string[] {
  return Array.from(new Set(values))
}

function dedupeNumbers(values: number[]): number[] {
  return Array.from(new Set(values))
}

function laneAt(segment: LineageSegment, row: number): number {
  const span = segment.toRow - segment.fromRow
  if (span === 0) {
    return segment.toLane
  }
  const t = (row - segment.fromRow) / span
  return segment.fromLane + (segment.toLane - segment.fromLane) * t
}

function segmentsCross(a: LineageSegment, b: LineageSegment): boolean {
  const overlapStart = Math.max(a.fromRow, b.fromRow)
  const overlapEnd = Math.min(a.toRow, b.toRow)
  if (overlapEnd - overlapStart <= 0) {
    return false
  }
  const epsilon = 1e-4
  const r0 = overlapStart + epsilon
  const r1 = overlapEnd - epsilon
  if (r1 <= r0) {
    return false
  }
  const d0 = laneAt(a, r0) - laneAt(b, r0)
  const d1 = laneAt(a, r1) - laneAt(b, r1)
  return d0 * d1 < 0
}

export function crossingPairsByTeam(segments: LineageSegment[]): Array<[string, string]> {
  const pairs = new Set<string>()
  for (let index = 0; index < segments.length; index++) {
    const a = segments[index]
    if (!a) {
      continue
    }
    for (let otherIndex = index + 1; otherIndex < segments.length; otherIndex++) {
      const b = segments[otherIndex]
      if (!b || a.team === b.team) {
        continue
      }
      if (!segmentsCross(a, b)) {
        continue
      }
      const left = a.team.localeCompare(b.team) <= 0 ? a.team : b.team
      const right = left === a.team ? b.team : a.team
      pairs.add(`${left}|${right}`)
    }
  }
  return Array.from(pairs.values()).sort().map((pair) => {
    const [left, right] = pair.split('|')
    return [left ?? '', right ?? '']
  })
}

type Point = { x: number, y: number }

function xForLane(lane: number): number {
  return PADDING_LEFT + lane * LANE_GAP
}

function yForRow(layout: LineageLayoutData, row: number): number {
  const clampedRow = Math.min(Math.max(row, 0), layout.rowYByRow.length - 1)
  return PADDING_TOP + (layout.rowYByRow[clampedRow] ?? layout.innerHeight)
}

function sampleCubic(p0: Point, p1: Point, p2: Point, p3: Point, steps = 24): Point[] {
  const points: Point[] = []
  for (let index = 0; index <= steps; index++) {
    const t = index / steps
    const mt = 1 - t
    const x = mt * mt * mt * p0.x + 3 * mt * mt * t * p1.x + 3 * mt * t * t * p2.x + t * t * t * p3.x
    const y = mt * mt * mt * p0.y + 3 * mt * mt * t * p1.y + 3 * mt * t * t * p2.y + t * t * t * p3.y
    points.push({ x, y })
  }
  return points
}

function sampleQuadratic(p0: Point, p1: Point, p2: Point, steps = 18): Point[] {
  const points: Point[] = []
  for (let index = 0; index <= steps; index++) {
    const t = index / steps
    const mt = 1 - t
    const x = mt * mt * p0.x + 2 * mt * t * p1.x + t * t * p2.x
    const y = mt * mt * p0.y + 2 * mt * t * p1.y + t * t * p2.y
    points.push({ x, y })
  }
  return points
}

function segmentPolyline(layout: LineageLayoutData, segment: LineageSegment): Point[] {
  const x1 = xForLane(segment.fromLane)
  const y1 = yForRow(layout, segment.fromRow)
  const x2 = xForLane(segment.toLane)
  const y2 = yForRow(layout, segment.toRow)
  const dy = y2 - y1
  if (segment.fromLane === segment.toLane || Math.abs(dy) < 10) {
    return [{ x: x1, y: y1 }, { x: x2, y: y2 }]
  }
  if (segment.mergeCurveStyle === 'elbow') {
    const midY = y1 + dy * 0.58
    return [{ x: x1, y: y1 }, { x: x1, y: midY }, { x: x2, y: midY }, { x: x2, y: y2 }]
  }

  const direction = dy >= 0 ? 1 : -1
  const absDy = Math.max(Math.abs(dy), 1)
  const maxBend = Math.max(absDy - 2, 2)
  const bendHeight = Math.min(maxBend, Math.max(10, absDy * 0.35))
  const curveStartY = y2 - direction * bendHeight

  if (segment.mergeCurveStyle === 'arc') {
    const dx = x2 - x1
    const sign = dx >= 0 ? 1 : -1
    const radius = Math.min(Math.max(6, Math.abs(dx) * 0.38), Math.max(6, bendHeight * 0.9))
    const yTurn = y2 - direction * radius
    const xTurn = x1 + sign * radius
    const points = [{ x: x1, y: y1 }, { x: x1, y: curveStartY }]
    if (Math.abs(curveStartY - yTurn) > 0.5) {
      points.push({ x: x1, y: yTurn })
    }
    points.push(
      ...sampleQuadratic(
        { x: x1, y: yTurn },
        { x: x1, y: y2 },
        { x: xTurn, y: y2 },
      ),
      { x: x2, y: y2 },
    )
    return points
  }

  const c1y = curveStartY + direction * bendHeight * 0.18
  const c2y = y2 - direction * bendHeight * 0.2
  const points = [{ x: x1, y: y1 }, { x: x1, y: curveStartY }]
  points.push(
    ...sampleCubic(
      { x: x1, y: curveStartY },
      { x: x1, y: c1y },
      { x: x2, y: c2y },
      { x: x2, y: y2 },
    ),
  )
  return points
}

function crossProduct(a: Point, b: Point, c: Point): number {
  return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
}

function pointOnSegment(a: Point, b: Point, p: Point, epsilon = 1e-8): boolean {
  const minX = Math.min(a.x, b.x) - epsilon
  const maxX = Math.max(a.x, b.x) + epsilon
  const minY = Math.min(a.y, b.y) - epsilon
  const maxY = Math.max(a.y, b.y) + epsilon
  return p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY
}

function segmentsIntersect(a1: Point, a2: Point, b1: Point, b2: Point): boolean {
  const d1 = crossProduct(a1, a2, b1)
  const d2 = crossProduct(a1, a2, b2)
  const d3 = crossProduct(b1, b2, a1)
  const d4 = crossProduct(b1, b2, a2)
  const epsilon = 1e-8

  if (
    ((d1 > epsilon && d2 < -epsilon) || (d1 < -epsilon && d2 > epsilon))
    && ((d3 > epsilon && d4 < -epsilon) || (d3 < -epsilon && d4 > epsilon))
  ) {
    return true
  }

  if (Math.abs(d1) <= epsilon && pointOnSegment(a1, a2, b1)) return true
  if (Math.abs(d2) <= epsilon && pointOnSegment(a1, a2, b2)) return true
  if (Math.abs(d3) <= epsilon && pointOnSegment(b1, b2, a1)) return true
  if (Math.abs(d4) <= epsilon && pointOnSegment(b1, b2, a2)) return true
  return false
}

function pointDistance(a: Point, b: Point): number {
  const dx = a.x - b.x
  const dy = a.y - b.y
  return Math.sqrt(dx * dx + dy * dy)
}

function sharesEndpointIntersection(a1: Point, a2: Point, b1: Point, b2: Point): boolean {
  const epsilon = 1e-3
  const endpointsA = [a1, a2]
  const endpointsB = [b1, b2]
  for (const pa of endpointsA) {
    for (const pb of endpointsB) {
      if (pointDistance(pa, pb) <= epsilon) {
        return true
      }
    }
  }
  return false
}

export function renderedCrossingPairsByTeam(layout: LineageLayoutData): Array<[string, string]> {
  const pairs = new Set<string>()
  const polylines = layout.segments.map((segment) => ({ segment, points: segmentPolyline(layout, segment) }))
  for (let index = 0; index < polylines.length; index++) {
    const first = polylines[index]
    if (!first) {
      continue
    }
    for (let otherIndex = index + 1; otherIndex < polylines.length; otherIndex++) {
      const second = polylines[otherIndex]
      if (!second || first.segment.team === second.segment.team) {
        continue
      }
      let intersects = false
      for (let aIndex = 0; aIndex < first.points.length - 1 && !intersects; aIndex++) {
        const a1 = first.points[aIndex]
        const a2 = first.points[aIndex + 1]
        if (!a1 || !a2) {
          continue
        }
        for (let bIndex = 0; bIndex < second.points.length - 1; bIndex++) {
          const b1 = second.points[bIndex]
          const b2 = second.points[bIndex + 1]
          if (!b1 || !b2) {
            continue
          }
          if (!segmentsIntersect(a1, a2, b1, b2)) {
            continue
          }
          if (sharesEndpointIntersection(a1, a2, b1, b2)) {
            continue
          }
          intersects = true
          break
        }
      }
      if (!intersects) {
        continue
      }
      const left = first.segment.team.localeCompare(second.segment.team) <= 0 ? first.segment.team : second.segment.team
      const right = left === first.segment.team ? second.segment.team : first.segment.team
      pairs.add(`${left}|${right}`)
    }
  }
  return Array.from(pairs.values()).sort().map((pair) => {
    const [left, right] = pair.split('|')
    return [left ?? '', right ?? '']
  })
}
