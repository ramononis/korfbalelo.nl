<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import useTeamsStore from '@/stores/teams'
import useMatchesStore from '@/stores/matches'
import { fetchMetaData, metaData } from '@/simulator/SeasonSimulator.ts'
import MatchTable from '@/components/MatchTable.vue'
import type { MatchFixture } from '@/types'

const teamStore = useTeamsStore()
const matchesStore = useMatchesStore()
const loaded = ref(false)

const props = defineProps<{
  name1: string,
  name2: string,
}>()

// const team1 = computed(() => teamStore.teamsByName.get(props.name1)!)
// const team2 = computed(() => teamStore.teamsByName.get(props.name2)!)

const relevantMatches = computed(() => matchesStore.matchupMatches(props.name1, props.name2))

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
  await matchesStore.ensureAllMatchesLoaded(metaData.MAX_ID, metaData.MATCHES_VERSION)
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
