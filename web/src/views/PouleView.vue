<script setup lang="ts">
import type { MatchFixture, PouleData } from '@/types'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import Papa from 'papaparse'
import TeamComponent from '@/components/TeamComponent.vue'
import usePersistentStore from '@/stores/persistentStore'
import { useRouter } from 'vue-router'
import usePoulesStore from '@/stores/poules'
import MatchTable from '@/components/MatchTable.vue'
import { ACTIVE_SEASONS, type KnownSeasonName } from '@/season'
import { getWithdrawalReason, getWithdrawalsForTeams } from '@/simulator/SeasonTransitionRules'

const props = defineProps<{
  name: string,
  season: string
}>()
const router = useRouter()
const dates = computed(() => Array.from(pouleDataByDate.value.keys()).sort())
const firstDate = computed(() => dates.value[0] ?? '')
const lastDate = computed(() => dates.value[dates.value.length - 1] ?? '')
const isAtFirstDate = computed(() => dates.value.length === 0 || currentDate.value === firstDate.value)
const isAtLastDate = computed(() => dates.value.length === 0 || currentDate.value === lastDate.value)

const goToPreviousDate = () => {
  const currentIndex = dates.value.indexOf(currentDate.value)
  if (currentIndex > 0) {
    const previous = dates.value[currentIndex - 1]
    if (!previous) return
    clearTimeout(timeoutId)
    currentDate.value = previous
  }
}

const goToNextDate = () => {
  const currentIndex = dates.value.indexOf(currentDate.value)
  if (currentIndex < dates.value.length - 1) {
    const next = dates.value[currentIndex + 1]
    if (!next) return
    clearTimeout(timeoutId)
    currentDate.value = next
  }
}

let timeoutId: number | undefined = undefined

const goToFirstDate = () => {
  if (dates.value.length > 0) {
    const first = firstDate.value
    if (!first) return
    clearTimeout(timeoutId)
    currentDate.value = first
  }
}

const goToLastDate = () => {
  if (dates.value.length > 0) {
    const last = lastDate.value
    if (!last) return
    clearTimeout(timeoutId)
    currentDate.value = last
  }
}
const inTransition = ref(false)
const activeTab = ref<'uitslagen' | 'programma'>('uitslagen')
const currentDate = ref<string>('')
watch(currentDate, () => {
  inTransition.value = true
  timeoutId = window.setTimeout(() => {
    setProbabilities()
    timeoutId = window.setTimeout(() => {
      inTransition.value = false
    }, 1500)
  }, 1500)
})

const persistentStore = usePersistentStore()
const pouleDataByDate = ref<Map<string, PouleData>>(new Map())
const pouleData = computed(() => {
  return pouleDataByDate.value.get(currentDate.value) ?? null
})
const visibleProbabilities = ref<Array<boolean>>([])

const toggleProbabilities = (index: number) => {
  visibleProbabilities.value[index] = !visibleProbabilities.value[index]
}

function simulate(match: MatchFixture) {
  syncSelectedSeason()
  persistentStore.poule = props.name
  persistentStore.match = match
  router.push('/simulator')
}
type Probabilities = {
  pChampion: string
  pOnlyPromote: string
  pPromote: string
  pRelegate: string
  pStay: string
  rating: string
  rd: string
}
const propabilitiesByDate = ref<Map<string, Map<string, Probabilities>>>(new Map())
const propabilities = ref<Map<string, Probabilities>>(new Map())

function setProbabilities() {
  propabilities.value = propabilitiesByDate.value.get(currentDate.value) ?? new Map()
}

const isRelevant = computed(() => persistentStore.relevantTeams?.relevantPoules?.includes(props.name) && isAtLastDate.value)
const withdrawalNotes = computed(() =>
  getWithdrawalsForTeams(
    props.season,
    pouleData.value?.standing.map((team) => team.team.name) ?? [],
  ),
)

function withdrawalReason(teamName: string) {
  return getWithdrawalReason(props.season, teamName)
}

const fetchData = async () => {
  Papa.parse(`/csv/${props.season}/${props.name}.csv`, {
    download: true,
    delimiter: '\t',
    skipEmptyLines: true,
    dynamicTyping: true,
    complete: (results) => {
      const data = results.data as string[][]
      if (!data.length) {
        return
      }
      const header = data[0] as string[]
      // iterate over results.data starting from the second row:
      for (let i = 1; i < data.length; i++) {
        const row = data[i] as Array<string | number | null | undefined>
        const date = row[0]
        if (typeof date !== 'string') continue
        // todo row[1] is a string, parse to PouleData
        if (typeof row[1] !== 'string') continue
        pouleDataByDate.value.set(date, JSON.parse(row[1]) as PouleData)
        propabilitiesByDate.value.set(date, new Map())
        const probabilitiesForDate = propabilitiesByDate.value.get(date)
        if (!probabilitiesForDate) continue
        for (let i = 2; i < row.length; i += 5) {
          const team = header[i + 1]
          if (typeof team !== 'string') continue
          if (row[i + 3] !== undefined && row[i + 3] !== null) {
            const championProbability = Number(row[i])
            const promoteProbability = Number(row[i + 1])
            const relegateProbability = Number(row[i + 2])
            probabilitiesForDate.set(team, {
              pChampion: (championProbability * 100).toFixed(2),
              pOnlyPromote: (Math.max(0, promoteProbability - championProbability) * 100).toFixed(2),
              pPromote: (promoteProbability * 100).toFixed(2),
              pRelegate: (relegateProbability * 100).toFixed(2),
              pStay: ((1.0 - promoteProbability - relegateProbability) * 100).toFixed(2),
              rating: Number(row[i + 3]).toFixed(0),
              rd: Number(row[i + 4]).toFixed(0)
            })
          }
        }
      }
      const latestRow = data[data.length - 1]
      const latestDate = latestRow?.[0]
      if (typeof latestDate === 'string') {
        currentDate.value = latestDate
      }
      setProbabilities()

      // Initialize visibleProbabilities only after pouleData is set
      if (pouleData.value?.standing) {
        visibleProbabilities.value = Array(pouleData.value.standing.length).fill(false)
      }
    }
  })
}
const poulesStore = usePoulesStore()

function syncSelectedSeason() {
  if (ACTIVE_SEASONS.some((season) => season.seasonName === props.season)) {
    persistentStore.season = props.season as KnownSeasonName
  }
}

onMounted(() => {
  syncSelectedSeason()
  fetchData()
  poulesStore.fetchTierData(props.season)
})

watch(() => props.season, () => {
  syncSelectedSeason()
})

onUnmounted(() => {
  clearTimeout(timeoutId)
})
</script>

<template>
  <div class="poule">
    <h1>{{ props.name }}</h1>
    <div>
      <button @click="goToFirstDate" :disabled="isAtFirstDate" aria-label="First">«</button>
      <button @click="goToPreviousDate" :disabled="isAtFirstDate" aria-label="Previous">‹</button>
      <span>Datum: {{ currentDate }}</span>
      <button @click="goToNextDate" :disabled="isAtLastDate" aria-label="Next">›</button>
      <button @click="goToLastDate" :disabled="isAtLastDate" aria-label="Last">»</button>
    </div>

    <div v-if="pouleData">
      <h2>Stand</h2>
      <i v-if="name === 'KL'">
        Kans op kampioenschap betekent hier kampioen van Nederland (rekening houdend met playoffs en finale)!
      </i>
      <table>
        <thead>
        <tr>
          <th>#</th>
          <th class="name-column"></th> <!-- Add class to header -->
          <th>G</th>
          <th>P</th>
          <th>W</th>
          <th>G</th>
          <th>V</th>
          <th>+</th>
          <th>-</th>
          <th>±</th>
          <th style="width: 180px">Kansen</th>
        </tr>
        </thead>
        <TransitionGroup tag="tbody" name="fade" class="container">
          <tr v-for="(team, index) in pouleData.standing" :key="team.team.name">
            <td>{{ index + 1 }}</td>
            <td class="name-column">
              <div class="team-line">
                <TeamComponent :team-name="team.team.name" :rating="propabilities.get(team.team.name)?.rating"/>
                <span
                  v-if="withdrawalReason(team.team.name)"
                  class="withdrawal-marker"
                  :title="withdrawalReason(team.team.name) ?? undefined"
                >*</span>
              </div>
            </td>
            <td>{{ team.stats.won + team.stats.draw + team.stats.lost }}</td>
            <td>
              {{ team.stats.points }}
              <template v-if="team.stats.penalties.points">(-{{ team.stats.penalties.points }})</template>
            </td>
            <td>{{ team.stats.won }}</td>
            <td>{{ team.stats.draw }}</td>
            <td>{{ team.stats.lost }}</td>
            <td>{{ team.stats.goals.for }}</td>
            <td>{{ team.stats.goals.against }}</td>
            <td>{{ team.stats.goals.for - team.stats.goals.against }}</td>
            <td>
              <div class="probability-container">
                <div class="probability-bar-line">
                  <div class="probability-bar">
                    <div class="champion" :title="propabilities.get(team.team.name)?.pChampion + '%'" :style="{ width: propabilities.get(team.team.name)?.pChampion + '%' }"></div>
                    <div class="promote" :title="propabilities.get(team.team.name)?.pPromote + '%'" :style="{ width: propabilities.get(team.team.name)?.pOnlyPromote + '%' }"></div>
                    <div class="relegate" :title="propabilities.get(team.team.name)?.pRelegate + '%'" :style="{ width: propabilities.get(team.team.name)?.pRelegate + '%' }"></div>
                  </div>
                  <button class="expand-button" @click="toggleProbabilities(index)">
                    <span v-if="visibleProbabilities[index]">&#x25B2;</span> <!-- Upwards filled arrowhead -->
                    <span v-else>&#x25BC;</span> <!-- Downwards filled arrowhead -->
                  </button>
                </div>
                <div v-if="visibleProbabilities[index]" class="probability-values">
                  <div v-if="propabilities.get(team.team.name)?.pChampion !== '0.00'">
                    Kampioen: {{ propabilities.get(team.team.name)?.pChampion }}%
                  </div>
                  <div v-if="propabilities.get(team.team.name)?.pPromote !== '0.00' && propabilities.get(team.team.name)?.pPromote !== propabilities.get(team.team.name)?.pChampion">
                    Promotie: {{ propabilities.get(team.team.name)?.pPromote }}%
                  </div>
                  <div v-if="propabilities.get(team.team.name)?.pStay !== '0.00'">
                    Handhaving: {{ propabilities.get(team.team.name)?.pStay }}%
                  </div>
                  <div v-if="propabilities.get(team.team.name)?.pRelegate !== '0.00'">
                    Degradatie: {{ propabilities.get(team.team.name)?.pRelegate }}%
                  </div>
                </div>
              </div>
            </td>
          </tr>
        </TransitionGroup>
      </table>
      <div v-if="withdrawalNotes.length" class="withdrawal-notes">
        <p v-for="note in withdrawalNotes" :key="note.teamName">
          * {{ note.teamName }}: {{ note.reason }}
        </p>
      </div>

      <!-- Tab Navigation -->
      <div class="tabs">
        <button :class="{ active: activeTab === 'uitslagen' }" @click="activeTab = 'uitslagen'">Uitslagen</button>
        <button :class="{ active: activeTab === 'programma' }" @click="activeTab = 'programma'">Programma</button>
      </div>

      <!-- Tab Content -->
      <div>
        <h2 v-if="activeTab === 'programma'">Programma</h2>
        <h2 v-else>Uitslagen</h2>
        <MatchTable :matches="activeTab === 'uitslagen' ? pouleData?.results : pouleData?.fixtures"
                    :with-results="activeTab === 'uitslagen'"
                    :with-simulation="!!persistentStore.simulationTeam && isRelevant && activeTab === 'programma'"
                    @simulate="simulate"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.poule {
  padding: 20px;
}

h1, h2 {
  margin-bottom: 1rem;
}

.probability-container {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.probability-bar-line {
  display: flex;
  width: 100%;
  align-items: center;
}

.probability-bar {
  display: flex;
  height: 20px;
  width: calc(100% - 40px); /* Adjust width to fit with button */
  margin-left: 10px; /* Add some space between button and bar */
  background-color: white;
  border: 1px solid var(--color-text);
  border-radius: 4px;
  overflow: hidden;
  position: relative; /* Position relative to position the relegate segment */
}

.probability-bar div {
  height: 100%;
  transition: width 1.5s ease-in-out; /* 0.3s delay */
  will-change: width;
}

.champion {
  background-color: gold;
  flex-shrink: 0;
}

.promote {
  background-color: green;
  flex-shrink: 0;
}

.relegate {
  position: absolute;
  right: 0;
  background-color: red;
  flex-shrink: 0;
}

.expand-button {
  border: none;
  cursor: pointer;
  margin: 0 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 30px;
}

.expand-button:hover {
  background-color: var(--color-background-mute);
}

.name-column {
  width: 1%; /* Automatically shrink to fit content */
  white-space: nowrap; /* Prevent wrapping to minimize width */
}

.team-line {
  display: flex;
  align-items: baseline;
  gap: 0.2rem;
}

.withdrawal-marker {
  color: #f0c24b;
  font-weight: 700;
  cursor: help;
}

.withdrawal-notes {
  margin-top: 0.75rem;
  font-size: 0.9rem;
  max-width: 32rem;
  white-space: normal;
  overflow-wrap: anywhere;
}

.withdrawal-notes p {
  margin: 0.15rem 0 0;
}

ul {
  list-style: none;
  padding: 0;
}

li {
  padding: 0.5rem 0;
}

strong {
  padding-left: 20px;
  display: block;
}

ul ul {
  margin-top: 0;
}

h2 {
  margin-top: 2rem;
}

/* 1. declare transition */
.fade-move,
.fade-enter-active,
.fade-leave-active {
  transition: all 1.5s cubic-bezier(0.55, 0, 0.1, 1);
}

/* 2. declare enter from and leave to state */
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: scaleY(0.01) translate(30px, 0);
}

/* 3. ensure leaving items are taken out of layout flow so that moving
      animations can be calculated correctly. */
.fade-leave-active {
  position: absolute;
}
.tabs {
  margin-top: 1rem;
}

ul {
  list-style: none;
  padding: 0;
}

li {
  padding: 0.5rem 0;
}
</style>
