<script setup lang="ts">
// AI-assisted: computes global match record tables from the generated aggregate CSVs.
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MatchTable from '@/components/MatchTable.vue'
import StatsDateRangeControls from '@/components/StatsDateRangeControls.vue'
import { fetchMetaData, metaData } from '@/simulator/SeasonSimulator'
import useMatchesStore from '@/stores/matches'
import type { MatchFixture } from '@/types'

const CURRENT_SEASON_START = '2025-08-01'
const TOP_LIST_SIZE = 10

type StatRow = {
  match: MatchFixture
  value: number
}

type StatSection = {
  id: string
  title: string
  rows: StatRow[]
}

const route = useRoute()
const router = useRouter()
const matchesStore = useMatchesStore()

const loaded = ref(false)
const fromDate = ref(routeDate('from') ?? CURRENT_SEASON_START)
const toDate = ref(routeDate('to') ?? '')
let disposed = false
let syncingFromRoute = false

const latestMatchDate = computed(() => matchesStore.latestMatchDate)
const earliestMatchDate = computed(() => matchesStore.earliestMatchDate)
const effectiveToDate = computed(() => toDate.value || latestMatchDate.value)
const stats = computed(() =>
  buildStats(matchesStore.matchesInRange(fromDate.value, effectiveToDate.value)),
)

const simpleStats = computed(() => [
  { name: 'Aantal wedstrijden', value: stats.value.matchCount.toString() },
  { name: 'Thuiswinst', value: withPercentage(stats.value.homeWins, stats.value.matchCount) },
  { name: 'Gelijkspel', value: withPercentage(stats.value.draws, stats.value.matchCount) },
  { name: 'Uitwinst', value: withPercentage(stats.value.awayWins, stats.value.matchCount) },
  { name: 'Gemiddeld aantal goals', value: stats.value.averageGoals },
])

const statSections = computed<StatSection[]>(() => stats.value.sections)

function routeDate(param: 'from' | 'to'): string | null {
  return validDate(paramValue(route.params[param]))
}

function paramValue(value: unknown): string | null {
  if (typeof value === 'string') {
    return value
  }
  if (Array.isArray(value) && typeof value[0] === 'string') {
    return value[0]
  }
  return null
}

function validDate(value: string | null): string | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null
  }
  return Number.isNaN(new Date(value).getTime()) ? null : value
}

function withPercentage(numerator: number, denominator: number): string {
  if (denominator === 0) {
    return '0 (0.0%)'
  }
  return `${numerator} (${(numerator / denominator * 100).toFixed(1)}%)`
}

async function loadMatches() {
  await fetchMetaData()
  await matchesStore.ensureAllMatchesLoaded(metaData.MAX_ID, metaData.MATCHES_VERSION)
  if (disposed) {
    return
  }
  loaded.value = true
  syncRouteToDates()
  await scrollToRouteHash()
}

function buildStats(matches: MatchFixture[]) {
  const sections = createEmptySections()
  let homeWins = 0
  let awayWins = 0
  let draws = 0
  let totalGoals = 0

  for (const match of matches) {
    const matchTotalGoals = match.homeScore + match.awayScore
    totalGoals += matchTotalGoals

    if (match.homeScore > match.awayScore) {
      homeWins++
    } else if (match.awayScore > match.homeScore) {
      awayWins++
    } else {
      draws++
    }

    addTopRow(sections[0]!.rows, match, Math.max(match.homeScore, match.awayScore))
    if (match.homeScore !== match.awayScore) {
      addTopRow(sections[1]!.rows, match, Math.abs(match.homeScore - match.awayScore))
    }
    addTopRow(sections[2]!.rows, match, matchTotalGoals)
    if (match.homeScore === match.awayScore) {
      addTopRow(sections[3]!.rows, match, matchTotalGoals)
    }
    if (match.homeScore !== match.awayScore) {
      addTopRow(sections[4]!.rows, match, Math.min(match.homeScore, match.awayScore))
    }
  }

  return {
    matchCount: matches.length,
    homeWins,
    awayWins,
    draws,
    averageGoals: matches.length === 0 ? '0.0' : (totalGoals / matches.length).toFixed(1),
    sections,
  }
}

function createEmptySections(): StatSection[] {
  return [
    { id: 'highest-team-score', title: 'Hoogste score door een team', rows: [] },
    { id: 'highest-win-margin', title: 'Hoogste winstmarge', rows: [] },
    { id: 'highest-total-goals', title: 'Hoogste totaalscore', rows: [] },
    { id: 'highest-draws', title: 'Hoogste gelijke spelen', rows: [] },
    { id: 'highest-losing-score', title: 'Hoogste score voor een verliezend team', rows: [] },
  ]
}

function addTopRow(rows: StatRow[], match: MatchFixture, value: number) {
  rows.push({ match, value })
  rows.sort(compareStatRows)
  if (rows.length > TOP_LIST_SIZE) {
    rows.pop()
  }
}

function compareStatRows(a: StatRow, b: StatRow): number {
  return b.value - a.value ||
    b.match.date.localeCompare(a.match.date) ||
    a.match.home.localeCompare(b.match.home) ||
    a.match.away.localeCompare(b.match.away)
}

function syncDatesFromRoute() {
  syncingFromRoute = true
  fromDate.value = routeDate('from') ?? CURRENT_SEASON_START
  toDate.value = routeDate('to') ?? ''
  syncingFromRoute = false
}

function syncRouteToDates() {
  if (syncingFromRoute || !loaded.value || !validDate(fromDate.value) || !validDate(effectiveToDate.value)) {
    return
  }
  if (
    route.name === 'global-stats-range' &&
    route.params.from === fromDate.value &&
    route.params.to === effectiveToDate.value
  ) {
    return
  }
  router.replace({
    name: 'global-stats-range',
    params: { from: fromDate.value, to: effectiveToDate.value },
  })
}

function clearRouteHash() {
  if (!route.hash || !validDate(fromDate.value) || !validDate(effectiveToDate.value)) {
    return
  }
  router.replace({
    name: 'global-stats-range',
    params: { from: fromDate.value, to: effectiveToDate.value },
  })
}

async function scrollToRouteHash() {
  if (!route.hash) {
    return
  }
  await nextTick()
  document.querySelector(route.hash)?.scrollIntoView()
}

watch(() => [route.params.from, route.params.to], () => {
  syncDatesFromRoute()
})

watch([fromDate, effectiveToDate], () => {
  syncRouteToDates()
})

watch(() => route.hash, () => {
  scrollToRouteHash()
})

onMounted(() => {
  loadMatches()
})

onUnmounted(() => {
  disposed = true
})
</script>

<template>
  <div class="global-stats">
    <h1>Alle wedstrijdstatistieken</h1>

    <StatsDateRangeControls
      v-model:from-date="fromDate"
      v-model:to-date="toDate"
      :earliest-date="earliestMatchDate"
      :latest-date="latestMatchDate"
      @shortcut="clearRouteHash"
    />

    <template v-if="loaded">
      <div class="stat-summary">
        <div v-for="stat in simpleStats" :key="stat.name" class="summary-item">
          <span>{{ stat.name }}</span>
          <strong>{{ stat.value }}</strong>
        </div>
      </div>

      <p v-if="stats.matchCount === 0">Geen wedstrijden gevonden in deze periode.</p>

      <section
        v-for="section in statSections"
        :id="section.id"
        :key="section.id"
        class="stat-section"
      >
        <h2>{{ section.title }}</h2>
        <MatchTable
          v-if="section.rows.length > 0"
          :matches="section.rows.map((row) => row.match)"
          with-results
          :sort-by-date="false"
          :group-by-date="false"
          show-date-per-match
        />
        <p v-else>Geen wedstrijden voor deze categorie.</p>
      </section>
    </template>
    <p v-else>Even wachten...</p>
  </div>
</template>

<style scoped>
.global-stats {
  max-width: 1200px;
}

.stat-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-bottom: 1.2rem;
}

.summary-item {
  background: var(--color-background-soft);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  min-width: 150px;
  padding: 0.65rem 0.8rem;
}

.summary-item span {
  font-size: 0.85rem;
  opacity: 0.8;
}

.stat-section {
  scroll-margin-top: 1rem;
}

.stat-section h2 {
  margin-top: 1.6rem;
}
</style>
