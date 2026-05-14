<script setup lang="ts">
// AI-assisted: shared date range controls for match statistics pages.
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  fromDate: string
  toDate: string
  earliestDate?: string
  latestDate?: string
  seasonStartMonth?: number
  seasonStartDay?: number
}>(), {
  earliestDate: '',
  latestDate: '',
  seasonStartMonth: 8,
  seasonStartDay: 1,
})

const emit = defineEmits<{
  'update:fromDate': [value: string]
  'update:toDate': [value: string]
  shortcut: [value: string]
}>()

const fromModel = computed({
  get: () => props.fromDate,
  set: (value: string) => emit('update:fromDate', value),
})

const toModel = computed({
  get: () => props.toDate,
  set: (value: string) => emit('update:toDate', value),
})

const hasMatches = computed(() => Boolean(props.earliestDate && props.latestDate))
const activeSeasonStart = computed(() => seasonStartFor(props.fromDate || props.latestDate))
const earliestSeasonStart = computed(() => props.earliestDate ? seasonStartFor(props.earliestDate) : '')
const latestSeasonStart = computed(() => props.latestDate ? seasonStartFor(props.latestDate) : '')
const latestSeasonEnd = computed(() => latestSeasonStart.value ? seasonEndFor(latestSeasonStart.value) : '')
const currentSeasonStart = computed(() => props.latestDate ? seasonStartFor(props.latestDate) : '')
const currentSeasonEnd = computed(() => currentSeasonStart.value ? seasonEndFor(currentSeasonStart.value) : '')
const isCurrentSeasonSelected = computed(() =>
  props.fromDate === currentSeasonStart.value && props.toDate === currentSeasonEnd.value,
)
const canSelectPreviousSeason = computed(() =>
  hasMatches.value && (!earliestSeasonStart.value || activeSeasonStart.value > earliestSeasonStart.value),
)
const canSelectNextSeason = computed(() =>
  hasMatches.value && (!latestSeasonStart.value || activeSeasonStart.value < latestSeasonStart.value),
)

function selectCurrentSeason() {
  if (!props.latestDate) {
    return
  }
  emit('shortcut', 'current-season')
  const from = seasonStartFor(props.latestDate)
  selectRange(from, seasonEndFor(from))
}

function selectPreviousSeason() {
  if (!canSelectPreviousSeason.value) {
    return
  }
  emit('shortcut', 'previous-season')
  selectSeason(-1)
}

function selectNextSeason() {
  if (!canSelectNextSeason.value) {
    return
  }
  emit('shortcut', 'next-season')
  selectSeason(1)
}

function selectSeason(offset: number) {
  const from = addYears(activeSeasonStart.value, offset)
  selectRange(from, seasonEndFor(from))
}

function selectFullHistory() {
  if (!props.earliestDate || !props.latestDate) {
    return
  }
  emit('shortcut', 'full-history')
  emit('update:fromDate', props.earliestDate)
  emit('update:toDate', props.latestDate)
}

function selectRange(from: string, to: string) {
  let nextFrom = from
  let nextTo = to
  if (nextTo < nextFrom) {
    nextTo = nextFrom
  }
  emit('update:fromDate', nextFrom)
  emit('update:toDate', nextTo)
}

function seasonStartFor(value: string): string {
  const parsed = parseIsoDate(value)
  if (!parsed) {
    return ''
  }
  let seasonYear = parsed.year
  if (
    parsed.month < props.seasonStartMonth ||
    (parsed.month === props.seasonStartMonth && parsed.day < props.seasonStartDay)
  ) {
    seasonYear -= 1
  }
  return formatIsoDate(seasonYear, props.seasonStartMonth, props.seasonStartDay)
}

function seasonEndFor(seasonStart: string): string {
  const parsed = parseIsoDate(seasonStart)
  if (!parsed) {
    return seasonStart
  }
  const nextStart = new Date(Date.UTC(parsed.year + 1, props.seasonStartMonth - 1, props.seasonStartDay))
  nextStart.setUTCDate(nextStart.getUTCDate() - 1)
  return formatUtcDate(nextStart)
}

function addYears(value: string, years: number): string {
  const parsed = parseIsoDate(value)
  if (!parsed) {
    return value
  }
  return formatIsoDate(parsed.year + years, parsed.month, parsed.day)
}

function parseIsoDate(value: string): { year: number, month: number, day: number } | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) {
    return null
  }
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(Date.UTC(year, month - 1, day))
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) {
    return null
  }
  return { year, month, day }
}

function formatIsoDate(year: number, month: number, day: number): string {
  return [
    year.toString().padStart(4, '0'),
    month.toString().padStart(2, '0'),
    day.toString().padStart(2, '0'),
  ].join('-')
}

function formatUtcDate(date: Date): string {
  return formatIsoDate(date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate())
}
</script>

<template>
  <div class="date-range-controls">
    <div class="date-inputs">
      <label>
        Vanaf datum
        <input
          type="date"
          v-model="fromModel"
          :min="earliestSeasonStart || earliestDate || undefined"
          :max="toDate || latestSeasonEnd || latestDate || undefined"
        >
      </label>
      <label>
        T/m datum
        <input
          type="date"
          v-model="toModel"
          :min="fromDate || earliestSeasonStart || earliestDate || undefined"
          :max="latestSeasonEnd || latestDate || undefined"
        >
      </label>
    </div>
    <div class="range-shortcuts">
      <button type="button" @click="selectPreviousSeason" :disabled="!canSelectPreviousSeason">
        Vorig seizoen
      </button>
      <button type="button" @click="selectCurrentSeason" :disabled="!hasMatches || isCurrentSeasonSelected">
        Huidig seizoen
      </button>
      <button type="button" @click="selectNextSeason" :disabled="!canSelectNextSeason">
        Volgend seizoen
      </button>
      <button type="button" @click="selectFullHistory" :disabled="!hasMatches">
        Hele historie
      </button>
    </div>
  </div>
</template>

<style scoped>
.date-range-controls {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.date-inputs,
.range-shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem;
}

.date-inputs label {
  display: flex;
  flex-direction: column;
  font-weight: 700;
  gap: 0.3rem;
}

.date-inputs input {
  background: var(--color-background-soft);
  border: 1px solid var(--color-border);
  color: var(--color-text);
  padding: 0.45rem 0.55rem;
}

.range-shortcuts button {
  background: var(--color-background-soft);
  border: 1px solid var(--color-border);
  color: var(--color-text);
  cursor: pointer;
  padding: 0.4rem 0.65rem;
}

.range-shortcuts button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>
