<script setup lang="ts">
import Dygraph from 'dygraphs'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import onMobile from '@/onMobile'
import { Team } from '@/types'
import { origins } from '@/origins'

defineOptions({
  name: 'DygraphChart',
})

type DygraphTouchContext = {
  initializeMouseDown: (event: MouseEvent, g: Dygraph, context: DygraphTouchContext) => void
}

const props = defineProps<{
  team: Team
}>()
const graphElement = ref<HTMLDivElement | null>(null)
const labels = new Map<string, number>();
const theOrigins = new Map<string, string[]>();
const isDarkMode = () => window.matchMedia("(prefers-color-scheme: dark)").matches

// TODO:watch team and call refilter()
watch(() => props.team, () => {
  if (!graph) {
    return
  }
  refilter()
})

let graph: Dygraph | null = null
onMounted(() => {
  if (!graphElement.value) {
    return
  }
  graph = new Dygraph(graphElement.value, '/graph.csv', {
    connectSeparatedPoints: true,
    highlightSeriesOpts: {
      strokeWidth: 1.5,
      strokeBorderWidth: 1,
      highlightCircleSize: 5,
      strokeBorderColor: window.matchMedia("(prefers-color-scheme: dark)").matches ? '#ffffff' : '#000000',
    },
    visibility: new Array(2000).fill(false),
    interactionModel: onMobile()
      ? {
        mousedown(event: MouseEvent, g: Dygraph, context: DygraphTouchContext) {
          context.initializeMouseDown(event, g, context)
        }
      }
      : null,
    legend: 'always',
    labelsShowZeroValues: false,
    stepPlot: true,
    colorValue: window.matchMedia("(prefers-color-scheme: dark)").matches ? 2 : 0.7,
    width: 886,
    height: 600,
    strokeWidth: 1,
    highlightSeriesBackgroundAlpha: 1,
    valueRange: [300, 2700]
  })
  for (const origin of origins.split('\n')) {
    const os = origin.split(',')
    const key = os[0]?.trim()
    if (!key) continue
    theOrigins.set(key, os.slice(1))
  }

  graph.ready(() => {
    if (!graph) {
      return
    }
    graph?.getLabels()?.forEach((l, i) => {
      labels.set(l, i - 1)
    })
    refilter()
  })
})

onUnmounted(() => {
  if (graph) {
    graph.destroy()
    graph = null
  }
  labels.clear()
  theOrigins.clear()
})

function refilter() {
  if (!graph) {
    return
  }
  const allLabels = graph.getLabels() ?? []
  const selectedSeriesLabel = findSelectedSeriesLabel(graphSeriesBaseLabel(props.team), allLabels)
  const { visibleSeries, selectedSeries } = resolveVisibleSeries(selectedSeriesLabel)

  const vs = graph.visibility()

  const ss: number[] = []
  const hs: number[] = []

  vs.forEach((b, i) => {
    const bb = visibleSeries.has(i)
    if (b !== bb) {
      (bb ? ss : hs).push(i)
    }
  })

  graph.setVisibility(ss, true)
  graph.setVisibility(hs, false)
  applySeriesStyles(visibleSeries, selectedSeries)
}

function graphSeriesBaseLabel(team: Team): string {
  return `${team.teamName} (${team.place})`
}

function parseReuseTimestamp(label: string): number {
  const match = label.match(/\[r:(\d{4}-\d{2}-\d{2})]$/)
  if (!match || !match[1]) {
    return Number.NEGATIVE_INFINITY
  }
  const timestamp = Date.parse(match[1])
  return Number.isNaN(timestamp) ? Number.NEGATIVE_INFINITY : timestamp
}

function findSelectedSeriesLabel(baseLabel: string, allLabels: string[]): string | null {
  if (allLabels.includes(baseLabel)) {
    return baseLabel
  }
  const candidates = allLabels
    .filter((label) => label.startsWith(`${baseLabel} [r:`))
    .sort((a, b) => parseReuseTimestamp(b) - parseReuseTimestamp(a))
  return candidates[0] ?? null
}

function resolveVisibleSeries(selectedLabel: string | null): { visibleSeries: Set<number>, selectedSeries: Set<number> } {
  const visibleSeries = new Set<number>()
  const selectedSeries = new Set<number>()
  if (!selectedLabel) {
    return { visibleSeries, selectedSeries }
  }

  const selectedIndex = labels.get(selectedLabel)
  if (selectedIndex != null && selectedIndex >= 0) {
    visibleSeries.add(selectedIndex)
    selectedSeries.add(selectedIndex)
  }

  const origins = theOrigins.get(selectedLabel) || []
  for (const origin of origins) {
    const originIndex = labels.get(origin)
    if (originIndex != null && originIndex >= 0) {
      visibleSeries.add(originIndex)
    }
  }
  return { visibleSeries, selectedSeries }
}

function relatedPalette(): string[] {
  if (isDarkMode()) {
    return [
      'rgba(255, 165, 89, 0.45)',
      'rgba(183, 227, 106, 0.45)',
      'rgba(195, 161, 255, 0.45)',
      'rgba(255, 143, 179, 0.45)',
      'rgba(127, 216, 182, 0.45)',
      'rgba(255, 209, 102, 0.45)',
      'rgba(168, 208, 255, 0.45)',
    ]
  }
  return [
    'rgba(180, 95, 6, 0.5)',
    'rgba(107, 142, 35, 0.5)',
    'rgba(123, 79, 163, 0.5)',
    'rgba(166, 61, 90, 0.5)',
    'rgba(42, 127, 98, 0.5)',
    'rgba(154, 106, 0, 0.5)',
    'rgba(63, 111, 176, 0.5)',
  ]
}

function applySeriesStyles(visibleSeries: Set<number>, selectedSeries: Set<number>) {
  if (!graph) {
    return
  }
  const allLabels = graph.getLabels() ?? []
  const series: Record<string, { color: string, strokeWidth: number }> = {}
  const palette = relatedPalette()
  const selectedColor = isDarkMode() ? '#00e5ff' : '#0037ff'

  let relatedIndex = 0
  for (const seriesIndex of Array.from(visibleSeries).sort((a, b) => a - b)) {
    const label = allLabels[seriesIndex + 1]
    if (!label || label === 'sd' || label === 'teams') {
      continue
    }
    if (selectedSeries.has(seriesIndex)) {
      series[label] = {
        color: selectedColor,
        strokeWidth: 2.4,
      }
    } else {
      series[label] = { color: palette[relatedIndex % palette.length]!, strokeWidth: 1.0 }
      relatedIndex++
    }
  }
  graph.updateOptions({ series })
}

</script>

<template>
  <div ref="graphElement" class="dygraph"></div>
</template>

<style>
.dygraph .dygraph-legend {
  margin-left: 64px;
}
.dygraph .dygraph-legend > span:not(.highlight) {
  display: none;
}
.dygraph .dygraph-legend > span[style] {
  display: inline;
}

</style>
