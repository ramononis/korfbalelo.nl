<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import TeamComponent from '@/components/TeamComponent.vue'
import type { DiscontinuedTeamRow } from '@/stores/discontinuedTeams'

type LineageEdgeType = 'rename' | 'merge_into' | 'fuse_new'
type LineageEventType = LineageEdgeType | 'spawn' | 'end'
type LineageSegmentKind = 'normal' | 'terminal_end' | 'terminal_continue'
type MergeCurveStyle = 'smooth' | 'elbow' | 'arc'

interface LineageEdge {
  id: string
  source: string
  target: string
  date: Date
  type: LineageEdgeType
}

interface LineageEvent {
  id: string
  date: Date
  target: string
  type: LineageEventType
  sources: string[]
}

interface LineageSegment {
  id: string
  team: string
  fromRow: number
  toRow: number
  fromLane: number
  toLane: number
  kind: LineageSegmentKind
  mergeCurveStyle?: MergeCurveStyle
}

interface LineageNode {
  id: string
  lane: number
  row: number
  event: LineageEvent
}

interface LineageLayout {
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

interface TeamPeriod {
  start: Date | null
  end: Date | null
}

interface ActiveLine {
  lane: number
  lastRow: number
}

interface SourceEntry extends ActiveLine {
  team: string
}

type TooltipState =
  | {
    kind: 'event'
    x: number
    y: number
    event: LineageEvent
  }
  | {
    kind: 'team'
    x: number
    y: number
    teamName: string
    startDate: Date | null
    endDate: Date | null
  }

const props = defineProps<{
  teamName: string
  discontinuedRows: DiscontinuedTeamRow[]
  teamStartDate?: Date | null
}>()

const LANE_GAP = 86
const PADDING_LEFT = 112
const PADDING_TOP = 20
const PADDING_RIGHT = 24
const PADDING_BOTTOM = 28
const DAY_MS = 24 * 60 * 60 * 1000
const YEAR_MS = 365.2425 * DAY_MS
const TIMELINE_PIXELS_PER_YEAR = 14
const DATE_LABEL_LINE_HEIGHT = 12
const MIN_EVENT_GAP = DATE_LABEL_LINE_HEIGHT * 2.925
const MAX_TIMELINE_HEIGHT = 960 * 0.7
const TOOLTIP_EDGE_PADDING = 12
const TOOLTIP_FLIP_Y = 96

const selectedEventId = ref<string | null>(null)
const selectedTeamName = ref<string | null>(null)
const tooltip = ref<TooltipState | null>(null)
const canvasRef = ref<HTMLElement | null>(null)
const svgRef = ref<SVGSVGElement | null>(null)

watch(() => props.teamName, () => {
  selectedEventId.value = null
  selectedTeamName.value = null
  tooltip.value = null
})

const layout = computed<LineageLayout | null>(() => {
  const rowByName = new Map(props.discontinuedRows.map((row) => [row.name, row]))
  const firstKnownStartDate = (team: string): Date | null => {
    if (team === props.teamName) {
      return props.teamStartDate ?? rowByName.get(team)?.firstMatchDate ?? null
    }
    return rowByName.get(team)?.firstMatchDate ?? null
  }
  const terminalEvent = buildTerminalEvent(props.teamName, rowByName.get(props.teamName))
  const edges = collectOriginEdges(props.teamName, props.discontinuedRows)
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

    const nearestKeepLaneDistance = (candidate: number): number => {
      if (keepLanes.length === 0) {
        return Number.POSITIVE_INFINITY
      }
      return keepLanes.reduce((best, lane) => Math.min(best, Math.abs(lane - candidate)), Number.POSITIVE_INFINITY)
    }

    let bestLane = uniqueCandidates[0] ?? medianLane
    let bestScore = Number.POSITIVE_INFINITY
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
    rememberSpawnDate(props.teamName, firstKnownStartDate(props.teamName) ?? terminalEvent.date)
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
    let terminalState = active.get(props.teamName)
    if (!terminalState) {
      const teamStart = spawnDateByTeam.get(props.teamName) ?? firstKnownStartDate(props.teamName) ?? terminalEvent.date
      const boundedStart = teamStart.getTime() <= terminalEvent.date.getTime() ? teamStart : terminalEvent.date
      const spawnRow = Math.min(rowForDate(boundedStart), terminalRow)
      const lane = allocateLane(spawnRow)
      terminalState = { lane, lastRow: spawnRow }
      active.set(props.teamName, terminalState)
      addSpawnNode(props.teamName, lane, boundedStart)
    }
    if (terminalState.lastRow < terminalRow) {
      segments.push({
        id: `${props.teamName}|${terminalState.lastRow}|${terminalRow}|terminal`,
        team: props.teamName,
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
    active.delete(props.teamName)
  }

  const maxTimelineRow = timelineEpochs.length > 0 ? timelineEpochs.length - 1 : 0
  const showCurrentNode = !terminalEvent
  const finalRow = showCurrentNode ? maxTimelineRow + 1 : maxTimelineRow
  const rowCount = finalRow + 1
  if (showCurrentNode && active.size === 0) {
    active.set(props.teamName, { lane: 0, lastRow: finalRow })
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
  let focusLane = active.get(props.teamName)?.lane
  if (focusLane == null) {
    for (let index = nodes.length - 1; index >= 0; index--) {
      const node = nodes[index]
      if (!node) {
        continue
      }
      if (node.event.target === props.teamName || node.event.sources.includes(props.teamName)) {
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
  const nowEpoch = Date.now()
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
})

watch(layout, () => {
  selectedEventId.value = null
  selectedTeamName.value = null
  tooltip.value = null
})

const svgWidth = computed(() => {
  if (!layout.value) {
    return 0
  }
  return PADDING_LEFT + PADDING_RIGHT + Math.max(0, layout.value.laneCount - 1) * LANE_GAP
})

const svgHeight = computed(() => {
  if (!layout.value) {
    return 0
  }
  return PADDING_TOP + PADDING_BOTTOM + layout.value.innerHeight
})

const teamPeriods = computed(() => {
  const periods = new Map<string, TeamPeriod>()
  if (!layout.value) {
    return periods
  }
  const rowByName = new Map(props.discontinuedRows.map((row) => [row.name, row]))
  const teams = new Set<string>([props.teamName])
  layout.value.segments.forEach((segment) => teams.add(segment.team))
  for (const team of teams) {
    const row = rowByName.get(team)
    periods.set(team, {
      start: row?.firstMatchDate ?? null,
      end: row?.lastEventDate ?? null,
    })
  }
  const sortedEvents = Array.from(layout.value.eventsById.values()).sort((a, b) => a.date.getTime() - b.date.getTime())
  for (const event of sortedEvents) {
    if (event.type === 'spawn') {
      const period = periods.get(event.target) ?? { start: null, end: null }
      period.start = period.start ?? event.date
      periods.set(event.target, period)
      continue
    }
    for (const source of event.sources) {
      const period = periods.get(source) ?? { start: null, end: null }
      period.end = event.date
      periods.set(source, period)
    }
    const targetPeriod = periods.get(event.target) ?? { start: null, end: null }
    if (event.type === 'rename' || event.type === 'fuse_new') {
      targetPeriod.start = event.date
    } else if (!targetPeriod.start) {
      targetPeriod.start = event.date
    }
    periods.set(event.target, targetPeriod)
  }
  const current = periods.get(props.teamName)
  if (current && !rowByName.has(props.teamName)) {
    current.end = null
    periods.set(props.teamName, current)
  }
  return periods
})

const legendTeams = computed(() => {
  if (!layout.value) {
    return []
  }
  const seen = new Set<string>()
  const names: string[] = []
  for (const segment of layout.value.segments) {
    if (!seen.has(segment.team)) {
      seen.add(segment.team)
      names.push(segment.team)
    }
  }
  if (!seen.has(props.teamName)) {
    names.push(props.teamName)
  }
  return names
})

function xForLane(lane: number): number {
  return PADDING_LEFT + lane * LANE_GAP
}

function yForRow(row: number): number {
  const graph = layout.value
  if (!graph) {
    return PADDING_TOP
  }
  const clampedRow = Math.min(Math.max(row, 0), graph.rowYByRow.length - 1)
  return PADDING_TOP + (graph.rowYByRow[clampedRow] ?? graph.innerHeight)
}

function teamColor(teamName: string): string {
  let hash = 0
  for (let index = 0; index < teamName.length; index++) {
    hash = (hash * 33 + teamName.charCodeAt(index)) | 0
  }
  const hue = Math.abs(hash) % 360
  return `hsl(${hue} 72% 50%)`
}

function lineWidth(teamName: string): number {
  return selectedTeamName.value === teamName ? 5 : 3
}

function lineOpacity(teamName: string): number {
  if (!selectedTeamName.value) {
    return 0.95
  }
  return selectedTeamName.value === teamName ? 1 : 0.35
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

function segmentDashArray(segment: LineageSegment): string | undefined {
  if (segment.kind === 'terminal_end') {
    return '4 5'
  }
  if (segment.kind === 'terminal_continue') {
    return '10 4'
  }
  return undefined
}

function segmentPath(segment: LineageSegment): string {
  const x1 = xForLane(segment.fromLane)
  const y1 = yForRow(segment.fromRow)
  const x2 = xForLane(segment.toLane)
  const y2 = yForRow(segment.toRow)
  const dy = y2 - y1
  if (segment.fromLane === segment.toLane || Math.abs(dy) < 10) {
    return `M ${x1} ${y1} L ${x2} ${y2}`
  }

  if (segment.mergeCurveStyle === 'elbow') {
    const midY = y1 + dy * 0.58
    return `M ${x1} ${y1} L ${x1} ${midY} L ${x2} ${midY} L ${x2} ${y2}`
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
    return `M ${x1} ${y1} L ${x1} ${curveStartY} L ${x1} ${yTurn} Q ${x1} ${y2} ${xTurn} ${y2} L ${x2} ${y2}`
  }

  const c1y = curveStartY + direction * bendHeight * 0.18
  const c2y = y2 - direction * bendHeight * 0.2
  return `M ${x1} ${y1} L ${x1} ${curveStartY} C ${x1} ${c1y} ${x2} ${c2y} ${x2} ${y2}`
}

function nodeTitle(node: { event: LineageEvent }): string {
  return `${eventText(node.event)} (${formatDate(node.event.date)})`
}

function formatDate(value: Date): string {
  return value.toLocaleDateString('nl-NL')
}

function eventText(event: LineageEvent): string {
  if (event.type === 'spawn') {
    return `${event.target} gestart`
  }
  if (event.type === 'end') {
    return `${event.sources[0] ?? event.target} gestopt`
  }
  if (event.type === 'rename' && event.sources.length === 1) {
    return `${event.sources[0]} hernoemd naar ${event.target}`
  }
  if (event.type === 'merge_into') {
    return `${event.sources.join(' + ')} opgegaan in ${event.target}`
  }
  if (event.type === 'fuse_new') {
    return `${event.sources.join(' + ')} gefuseerd tot ${event.target}`
  }
  return `${event.sources.join(' + ')} -> ${event.target}`
}

const renderedNodes = computed(() => {
  if (!layout.value) {
    return []
  }
  return layout.value.nodes.map((node) => ({
    ...node,
    eventText: eventText(node.event),
    eventDateText: formatDate(node.event.date),
  }))
})

const tooltipStyle = computed(() => {
  if (!tooltip.value) {
    return {}
  }
  let x = tooltip.value.x
  const y = tooltip.value.y
  const canvas = canvasRef.value
  if (canvas) {
    const minX = canvas.scrollLeft + TOOLTIP_EDGE_PADDING
    const maxX = canvas.scrollLeft + canvas.clientWidth - TOOLTIP_EDGE_PADDING
    x = Math.min(Math.max(x, minX), maxX)
  }
  return {
    left: `${x}px`,
    top: `${y}px`,
  }
})

const tooltipPlacement = computed<'above' | 'below'>(() => {
  if (!tooltip.value) {
    return 'above'
  }
  const canvas = canvasRef.value
  if (!canvas) {
    return 'above'
  }
  const relativeY = tooltip.value.y - canvas.scrollTop
  return relativeY < TOOLTIP_FLIP_Y ? 'below' : 'above'
})

function toCanvasPoint(svgX: number, svgY: number): { x: number, y: number } {
  const svg = svgRef.value
  const canvas = canvasRef.value
  if (!svg || !canvas) {
    return { x: svgX, y: svgY }
  }
  const renderedWidth = svg.clientWidth || Math.max(svgWidth.value, 1)
  const renderedHeight = svg.clientHeight || Math.max(svgHeight.value, 1)
  const scaleX = renderedWidth / Math.max(svgWidth.value, 1)
  const scaleY = renderedHeight / Math.max(svgHeight.value, 1)
  return {
    x: svgX * scaleX + canvas.scrollLeft,
    y: svgY * scaleY + canvas.scrollTop,
  }
}

function selectEvent(node: LineageNode, event: MouseEvent) {
  event.stopPropagation()
  selectedEventId.value = node.id
  selectedTeamName.value = null
  const point = toCanvasPoint(xForLane(node.lane), yForRow(node.row))
  tooltip.value = {
    kind: 'event',
    x: point.x,
    y: point.y,
    event: node.event,
  }
}

function selectTeamLine(segment: LineageSegment, event?: MouseEvent) {
  event?.stopPropagation()
  selectedTeamName.value = segment.team
  selectedEventId.value = null
  const point = toCanvasPoint(
    (xForLane(segment.fromLane) + xForLane(segment.toLane)) / 2,
    (yForRow(segment.fromRow) + yForRow(segment.toRow)) / 2,
  )
  tooltip.value = {
    kind: 'team',
    x: point.x,
    y: point.y,
    teamName: segment.team,
    startDate: teamPeriods.value.get(segment.team)?.start ?? null,
    endDate: teamPeriods.value.get(segment.team)?.end ?? null,
  }
}

function selectTeamLegend(teamName: string) {
  selectedTeamName.value = teamName
  selectedEventId.value = null
  tooltip.value = null
}

function clearTooltip() {
  tooltip.value = null
  selectedEventId.value = null
  selectedTeamName.value = null
}

function formatPeriodRange(startDate: Date | null, endDate: Date | null): string {
  const start = startDate ? formatDate(startDate) : 'onbekend'
  const end = endDate ? formatDate(endDate) : 'heden'
  return `(${start} - ${end})`
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
      type: row.fateType,
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
</script>

<template>
  <section v-if="layout" class="lineage">
    <div ref="canvasRef" class="lineage-canvas">
      <svg
        ref="svgRef"
        :viewBox="`0 0 ${svgWidth} ${svgHeight}`"
        :width="svgWidth"
        :height="svgHeight"
        @click="clearTooltip"
      >
        <g class="date-labels">
          <text
            v-for="rowLabel in layout.rowLabels"
            :key="`date-${rowLabel.row}`"
            :x="10"
            :y="yForRow(rowLabel.row) + 4"
          >
            {{ formatDate(rowLabel.date) }}
          </text>
        </g>

        <g class="segments">
          <g v-for="segment in layout.segments" :key="segment.id">
            <path
              :d="segmentPath(segment)"
              :stroke="teamColor(segment.team)"
              :stroke-width="lineWidth(segment.team)"
              :stroke-opacity="lineOpacity(segment.team)"
              :stroke-dasharray="segmentDashArray(segment)"
              fill="none"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path
              class="hitline"
              :d="segmentPath(segment)"
              fill="none"
              stroke-linejoin="round"
              @click.stop="selectTeamLine(segment, $event)"
            />
          </g>
        </g>

        <g class="nodes">
          <g
            v-for="node in renderedNodes"
            :key="node.id"
            class="event-node"
            :class="{
              selected: selectedEventId === node.id,
              'terminal-node': node.id.startsWith('terminal|'),
              'terminal-end': node.event.type === 'end',
            }"
            @click.stop="selectEvent(node, $event)"
          >
            <title>{{ nodeTitle(node) }}</title>
            <rect
              v-if="node.event.type === 'end'"
              :x="xForLane(node.lane) - 5.5"
              :y="yForRow(node.row) - 5.5"
              width="11"
              height="11"
              rx="2"
              :fill="teamColor(node.event.target)"
            />
            <circle
              v-else
              :cx="xForLane(node.lane)"
              :cy="yForRow(node.row)"
              :r="node.id.startsWith('terminal|') || node.event.sources.length > 1 ? 7 : 6"
              :fill="teamColor(node.event.target)"
              :stroke-dasharray="node.id.startsWith('terminal|') ? '4 3' : undefined"
            />
          </g>

        </g>
      </svg>

      <div
        v-if="tooltip"
        class="lineage-tooltip"
        :class="{ below: tooltipPlacement === 'below' }"
        :style="tooltipStyle"
        @click.stop
      >
        <template v-if="tooltip.kind === 'event'">
          <div class="tooltip-date">{{ formatDate(tooltip.event.date) }}</div>
          <div class="tooltip-text">{{ eventText(tooltip.event) }}</div>
        </template>
        <template v-else>
          <TeamComponent :team-name="tooltip.teamName" :show-rating="false" inline />
          <div class="tooltip-period">{{ formatPeriodRange(tooltip.startDate, tooltip.endDate) }}</div>
        </template>
      </div>
    </div>

    <div class="lineage-legend">
      <button
        v-for="lineTeam in legendTeams"
        :key="lineTeam"
        class="legend-item"
        :class="{ selected: selectedTeamName === lineTeam }"
        @click="selectTeamLegend(lineTeam)"
      >
        <span class="swatch" :style="{ backgroundColor: teamColor(lineTeam) }" />
        <TeamComponent :team-name="lineTeam" :show-rating="false" inline />
      </button>
    </div>

  </section>
</template>

<style scoped>
.lineage {
  margin-top: 1rem;
  margin-bottom: 1rem;
  padding: 0.85rem;
  border-radius: 8px;
  background: var(--color-background-soft);
}

.lineage h2 {
  margin: 0 0 0.4rem;
  font-size: 1.2rem;
}

.lineage-canvas {
  position: relative;
  overflow-x: auto;
  border-radius: 6px;
  background: color-mix(in srgb, var(--color-background) 85%, var(--color-border) 15%);
  padding: 0.4rem 0.2rem;
}

svg {
  display: block;
  min-width: 100%;
}

.date-labels text {
  font-size: 12px;
  line-height: 12px;
  fill: var(--color-text);
  opacity: 0.78;
}

.hitline {
  stroke: transparent;
  stroke-width: 14px;
  cursor: pointer;
}

.event-node {
  cursor: pointer;
}

.event-node circle {
  stroke: var(--color-background);
  stroke-width: 2;
}

.event-node rect {
  stroke: var(--color-background);
  stroke-width: 2;
}

.event-node.terminal-node circle {
  stroke-width: 3;
}

.event-node.selected circle {
  stroke: var(--color-text);
  stroke-width: 3;
}

.event-node.selected rect {
  stroke: var(--color-text);
  stroke-width: 3;
}

.lineage-legend {
  margin-top: 0.7rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.lineage-tooltip {
  position: absolute;
  transform: translate(-50%, calc(-100% - 10px));
  max-width: min(320px, calc(100% - 20px));
  padding: 0.45rem 0.55rem;
  border-radius: 6px;
  border: 1px solid var(--color-border-hover);
  background: color-mix(in srgb, var(--color-background) 84%, var(--color-border) 16%);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.22);
  z-index: 5;
}

.lineage-tooltip.below {
  transform: translate(-50%, 10px);
}

.tooltip-date {
  font-size: 0.9rem;
  opacity: 0.82;
}

.tooltip-text {
  margin-top: 0.2rem;
}

.tooltip-period {
  margin-top: 0.2rem;
  white-space: nowrap;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.2rem 0.4rem;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
}

.legend-item.selected {
  border-color: var(--color-border-hover);
  background: color-mix(in srgb, var(--color-background) 72%, var(--color-border) 28%);
}

.swatch {
  width: 12px;
  height: 12px;
  border-radius: 999px;
  flex-shrink: 0;
}

@media (max-width: 720px) {
  .lineage {
    padding: 0.6rem;
  }

  .lineage h2 {
    font-size: 1.05rem;
  }

  .lineage-tooltip {
    max-width: min(260px, calc(100% - 14px));
  }
}
</style>
