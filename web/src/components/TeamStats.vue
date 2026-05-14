<template>
  <div v-if="teamStore.loaded">
    <h1>Statistieken</h1>
    <StatsDateRangeControls
      v-model:from-date="fromDate"
      v-model:to-date="toDate"
      :earliest-date="earliestMatchDate"
      :latest-date="latestMatchDate"
    />
    <template v-if="loaded">
      <button @click="downloadMatchesAsCsv" :disabled="matches.length === 0">
        Download wedstrijden als CSV
      </button>
      <div
        style="display: flex; justify-content: space-between; padding: 8px; border-bottom: 1px solid #ccc; width: fit-content;"
        v-for="stat in simpleStats"
        :key="stat.name"
      >
        <span style="font-weight: bold; margin-right: 8px;">{{ stat.name }} : </span>
        <!-- Added margin-right for spacing -->
        <span>{{ stat.value }}</span>
      </div>
      <div>
        <h2>Meest voorkomende tegenstanders: </h2>
        <div v-for="[opponent, count] in opponentSortedByCount" :key="opponent">
          <router-link :to="`/matchup/${encodeURIComponent(team.name)}...${encodeURIComponent(opponent)}`">
            {{ opponent }}: {{ count }}
          </router-link>
        </div>
        <button @click="opponentSortedByCountShowAll = !opponentSortedByCountShowAll">
          {{ opponentSortedByCountShowAll ? '^' : 'v' }}
        </button>
      </div>
      <div>
        <h2>Sterk tegen: </h2>
        <div v-for="[opponent, eloDiff] in strongAgainstOpponents" :key="opponent">
          <router-link :to="`/matchup/${encodeURIComponent(team.name)}...${encodeURIComponent(opponent)}`">
            {{ opponent }}: {{ eloDiff.toFixed(1) }}
          </router-link>
        </div>
        <button @click="strongAgainstOpponentsShowAll = !strongAgainstOpponentsShowAll">
          {{ strongAgainstOpponentsShowAll ? '^' : 'v' }}
        </button>
      </div>
      <div>
        <h2>Zwak tegen: </h2>
        <div v-for="[opponent, eloDiff] in weakAgainstOpponents" :key="opponent">
          <router-link :to="`/matchup/${encodeURIComponent(team.name)}...${encodeURIComponent(opponent)}`">
            {{ opponent }}: {{ eloDiff.toFixed(1) }}
          </router-link>
        </div>
        <button @click="weakAgainstOpponentsShowAll = !weakAgainstOpponentsShowAll">
          {{ weakAgainstOpponentsShowAll ? '^' : 'v' }}
        </button>
      </div>

      <div v-for="msr in matchLists" :key="msr.name">
        <h2>{{ msr.name }}</h2>
        <MatchTable
          :matches="msr.msr"
          with-results
          :sort-by-date="false"
          :group-by-date="false"
          show-date-per-match
        />
      </div>
    </template>
    <div v-else>
      Even wachten...
    </div>
  </div>
</template>

<script setup lang="ts">
import { loser, type MatchFixture, type Team, winner } from '@/types'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import useTeamsStore from '@/stores/teams.ts'
import useMatchesStore from '@/stores/matches.ts'
import Papa from 'papaparse'
import { fetchMetaData, metaData } from '@/simulator/SeasonSimulator.ts'
import MatchTable from '@/components/MatchTable.vue'
import StatsDateRangeControls from '@/components/StatsDateRangeControls.vue'

const props = defineProps<{
  team: Team,
}>()

const teamStore = useTeamsStore()
const matchesStore = useMatchesStore()
const loaded = ref(false)
const fromDate = ref("1900-08-01")
const toDate = ref("")
const allTeamMatches = computed(() => matchesStore.matchesForTeam(props.team.name))
const latestMatchDate = computed(() =>
  allTeamMatches.value[allTeamMatches.value.length - 1]?.date ?? '',
)
const earliestMatchDate = computed(() =>
  allTeamMatches.value[0]?.date ?? '',
)
const effectiveToDate = computed(() => toDate.value || latestMatchDate.value)
const matches = computed(() =>
  matchesStore.teamMatchesInRange(props.team.name, fromDate.value, effectiveToDate.value),
)
const nMatches = computed(() => matches.value.length)
const wonMatches = computed(() => matches.value.filter(m => winner(m) === props.team.name).length)
const lostMatches = computed(() => matches.value.filter(m => loser(m) === props.team.name).length)
const drawnMatches = computed(() => matches.value.filter(m => winner(m) === null).length)
let disposed = false
const opponentSortedByCountShowAll = ref(false)
const opponentSortedByCount = computed(() => {
  const opponentsCount: Record<string, number> = {}

  matches.value.forEach(match => {
    const opponent = match.home === props.team.name ? match.away : match.home
    if (opponent) {
      opponentsCount[opponent] = (opponentsCount[opponent] || 0) + 1
    }
  })

  const result = Object.entries(opponentsCount)
    .sort(([, countA], [, countB]) => countB - countA) // Sort by count descending
  if (opponentSortedByCountShowAll.value) {
    return result
  } else {
    return result.slice(0, 10)
  }
})

const matchLists = computed(() => {
  const result = [
    {
      name: 'Hoogste score voor',
      calc(match: MatchFixture) {
        return match.home === props.team.name ? match.homeScore : match.awayScore
      }
    },
    {
      name: 'Hoogste score tegen',
      calc(match: MatchFixture) {
        return match.home !== props.team.name ? match.homeScore : match.awayScore
      }
    },
    {
      name: 'Hoogste totaalscore',
      calc(match: MatchFixture) {
        return match.homeScore + match.awayScore
      }
    },
    {
      name: 'Hoogste winstmarge',
      calc(match: MatchFixture) {
        return match.home === props.team.name ? (match.homeScore - match.awayScore) : (match.awayScore - match.homeScore)
      }
    },
    {
      name: 'Hoogste verliesmarge',
      calc(match: MatchFixture) {
        return match.home !== props.team.name ? (match.homeScore - match.awayScore) : (match.awayScore - match.homeScore)
      }
    }
  ]
  return result.map(({ name, calc }) => ({
    name, msr: [...matches.value].sort((a, b) => calc(b) - calc(a)).slice(0, 10).reverse(),
  }))
})

const opponentSortedByDiff = computed(() => {
  const opponentsDiff: Record<string, number> = {}
  const opponentsCount: Record<string, number> = {}

  matches.value.forEach(match => {
    const opponent = match.home === props.team.name ? match.away : match.home
    const ownRd = match.home === props.team.name ? match.homeRd : match.awayRd
    if (opponent && ownRd < 200) {
      const diff = match.home === props.team.name ? match.homeDiff : match.awayDiff
      opponentsDiff[opponent] = (opponentsDiff[opponent] || 0) + diff
      opponentsCount[opponent] = (opponentsCount[opponent] || 0) + 1
    }
  })

  // Object.entries(opponentsDiff).forEach(([opponent, diff]) => {
  //   opponentsDiff[opponent] = diff / opponentsCount[opponent]
  // })

  return Object.entries(opponentsDiff)
    .sort(([, countA], [, countB]) => countB - countA) // Sort by count descending
})
const strongAgainstOpponentsShowAll = ref(false)
const strongAgainstOpponents = computed(() => {
  const result = opponentSortedByDiff.value.filter(([, diff]) => diff > 0)
  if (strongAgainstOpponentsShowAll.value) {
    return result
  } else {
    return result.slice(0, 10)
  }
})
const weakAgainstOpponentsShowAll = ref(false)
const weakAgainstOpponents = computed(() => {
  const result = opponentSortedByDiff.value.filter(([, diff]) => diff < 0).reverse()
  if (weakAgainstOpponentsShowAll.value) {
    return result
  } else {
    return result.slice(0, 10)
  }
})
const simpleStats = computed(() => [
  {
    name: 'Aantal wedstrijden in database',
    value: nMatches.value,
  },
  {
    name: 'Aantal gewonnen wedstrijden',
    value: withPercentage(wonMatches.value, nMatches.value),
  },
  {
    name: 'Aantal gelijkspel wedstrijden',
    value: withPercentage(drawnMatches.value, nMatches.value),
  },
  {
    name: 'Aantal verloren wedstrijden',
    value: withPercentage(lostMatches.value, nMatches.value),
  }
])

function withPercentage(numerator: number, denominator: number) {
  return `${numerator} (${(numerator / denominator * 100).toFixed(1)}%)`
}

function downloadMatchesAsCsv() {
  const sortedMatches = [...matches.value].sort(
    (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()
  )

  const csv = Papa.unparse(
    sortedMatches.map(match => {
      const atHome = match.home === props.team.name
      const ownScore = atHome ? match.homeScore : match.awayScore
      const opponentScore = atHome ? match.awayScore : match.homeScore
      const scoreDiff = ownScore - opponentScore
      const outcome = scoreDiff > 0 ? 'win' : scoreDiff < 0 ? 'loss' : 'draw'
      const ownRating = atHome ? match.homeRating : match.awayRating
      const opponentRating = atHome ? match.awayRating : match.homeRating
      return {
        date: match.date,
        atHome: atHome ? 'home' : 'away',
        opponent: atHome ? match.away : match.home,
        ownScore: ownScore,
        opponentScore: opponentScore,
        scoreDiff: scoreDiff,
        outcome: outcome,
        ownRating: ownRating,
        opponentRating: opponentRating,
        ratingDifference: ownRating - opponentRating,
        pOwn: atHome ? match.pHome : match.pAway,
        pDraw: match.pDraw,
        pOpponent: atHome ? match.pAway : match.pHome,
        guessOwn: atHome ? match.guessHome : match.guessAway,
        guessOpponent: atHome ? match.guessAway : match.guessHome,
        ownRatingChange: atHome ? match.homeDiff : match.awayDiff,
        opponentRatingChange: atHome ? match.awayDiff : match.homeDiff,
        ownRd: atHome ? match.homeRd : match.awayRd,
        opponentRd: atHome ? match.awayRd : match.homeRd,
      }
    })
  )

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  const fileName = `${props.team.name.replace(/[\\/:*?"<>|]+/g, '_')}.csv`
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  await fetchMetaData()
  if (disposed) {
    return
  }
  await matchesStore.ensureAllMatchesLoaded(metaData.MAX_ID, metaData.MATCHES_VERSION)
  if (disposed) {
    return
  }
  loaded.value = true
})

onUnmounted(() => {
  disposed = true
  loaded.value = false
})
</script>

<style scoped>
</style>
