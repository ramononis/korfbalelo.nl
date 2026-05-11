<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import useTeamsStore from '@/stores/teams'
import useMatchesStore from '@/stores/matches'
import { fetchMetaData, metaData } from '@/simulator/SeasonSimulator.ts'
import type { MatchFixture } from '@/types'
import Papa from 'papaparse'
import MatchTable from '@/components/MatchTable.vue'

const teamStore = useTeamsStore()
const matchesStore = useMatchesStore()
const loaded = ref(false)

const props = defineProps<{
  name1: string,
  name2: string,
}>()

type MatchCsvRow = [
  string,
  string,
  string,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number
]


// const team1 = computed(() => teamStore.teamsByName.get(props.name1)!)
// const team2 = computed(() => teamStore.teamsByName.get(props.name2)!)

const relevantMatches = ref<MatchFixture[]>([])

async function loadMatches() {
  for (const data of matchesStore.matches.values()) {
    await new Promise<void>((resolve) => Papa.parse(data, {
        dynamicTyping: true,
        delimiter: ',',
        skipEmptyLines: true,
        complete: (results) => {
          const rows = results.data as MatchCsvRow[]
          rows.forEach(([date, home, away, homeScore, awayScore, homeRating, awayRating, pHome, pDraw, pAway, guessHome, guessAway, homeDiff, awayDiff, homeRd, awayRd]) => {
              if ((home === props.name1 && away === props.name2) || (away === props.name1 && home === props.name2))
                relevantMatches.value.push({
                  date: date,
                  home: home,
                  away: away,
                  homeScore: homeScore,
                  awayScore: awayScore,
                  homeRating: homeRating,
                  awayRating: awayRating,
                  pHome: pHome,
                  pDraw: pDraw,
                  pAway: pAway,
                  guessHome: guessHome,
                  guessAway: guessAway,
                  homeDiff: homeDiff,
                  awayDiff: awayDiff,
                  homeRd: homeRd,
                  awayRd: awayRd
                })
            }
          )
          resolve()
        }
      }
    ))
  }
}

function winner(match: MatchFixture): string | null {
  switch(Math.sign(match.homeScore - match.awayScore)) {
    case 1: return match.home
    case -1: return match.away
    default: return null
  }
}

const wins1 = computed(() => relevantMatches.value.filter(m => winner(m) === props.name1).length)
const wins2 = computed(() => relevantMatches.value.filter(m => winner(m) === props.name2).length)
const draws = computed(() => relevantMatches.value.filter(m => winner(m) === null).length)

onMounted(async () => {
  await fetchMetaData()
  await Promise.all(new Array(metaData.MAX_ID + 1).fill(0)
    .map((_, id) => {
      return matchesStore.fetchMatches(id)
    })
  )
  await loadMatches()
  loaded.value = true

})
</script>

<template>
  <div v-if="teamStore.loaded">
    <h1>{{ name1 }} vs {{ name2 }}</h1>

    <template v-if="loaded">
      <template v-if="relevantMatches.length > 0">
        <h2>Geschiedenis</h2>
        <div>Gewonnen door {{ name1 }}: {{ wins1 }} ({{ (wins1 / relevantMatches.length * 100).toFixed(1) }}%)</div>
        <div>Gewonnen door {{ name2 }}: {{ wins2 }} ({{ (wins2 / relevantMatches.length * 100).toFixed(1) }}%)</div>
        <div>Gelijkspel: {{ draws }} ({{ (draws / relevantMatches.length * 100).toFixed(1) }}%)</div>

        <MatchTable :matches="relevantMatches" with-results/>
        </template>
      <template v-else>
        <h2>Geen onderlinge wedstrijden bekend</h2>
        </template>
    </template>
    <div v-else>
      Even wachten...
    </div>
  </div>
</template>

<style scoped>

</style>
