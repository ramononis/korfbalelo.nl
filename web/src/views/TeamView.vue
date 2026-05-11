<template>
  <div class="team" v-if="team">
    <h1>{{ team.name }}</h1>
    <div class="team-info">
      <span class="info-item" :style="{ color: getRatingColor(team.rating) }">Rating: {{ team.formattedRating }}</span>
      <span class="info-item">Prestatie dit seizoen: {{ team.seasonRatingDiff }}</span>
      <span class="info-item">Actief van {{ team.formattedFirstMatchDate }} tot {{ team.formattedLastMatchDate }}</span>
      <span class="info-item">Plaats: {{ team.place }}</span>
      <div class="info-item fate-item" v-if="discontinuedTeam">
        <span>Status:</span>
        <FateComponent :fate="discontinuedTeam.fate" :fate-type="discontinuedTeam.fateType" :fate-teams="discontinuedTeam.fateTeams" />
      </div>
    </div>

    <button
            v-if="!discontinuedTeam && persistentStore.simulationTeam != team.name"
            @click="persistentStore.simulationTeam = team.name">Selecteer voor simulator</button>
    <div v-else-if="!discontinuedTeam">
      Dit team is geselecteerd voor simulatie.
      Ga naar een poule uit het huidige seizoen en selecteer een wedstrijd voor simulatie.
    </div>

    <div class="team-tabs" role="tablist" aria-label="Teamweergave">
      <button
        type="button"
        class="team-tab"
        :class="{ active: activeTab === 'graph' }"
        role="tab"
        :aria-selected="activeTab === 'graph'"
        @click="setActiveTab('graph')"
      >
        Ratinggrafiek
      </button>
      <button
        v-if="hasOriginTab"
        type="button"
        class="team-tab"
        :class="{ active: activeTab === 'origin' }"
        role="tab"
        :aria-selected="activeTab === 'origin'"
        @click="setActiveTab('origin')"
      >
        Geschiedenis
      </button>
      <button
        type="button"
        class="team-tab"
        :class="{ active: activeTab === 'stats' }"
        role="tab"
        :aria-selected="activeTab === 'stats'"
        @click="setActiveTab('stats')"
      >
        Statistieken
      </button>
    </div>

    <section class="tab-panels">
      <div v-show="activeTab === 'graph'" class="tab-panel">
        <Dygraph v-if="graphTabMounted" :team="team" />
      </div>
      <div v-if="hasOriginTab" v-show="activeTab === 'origin'" class="tab-panel">
        <TeamLineageGraph
          v-if="originTabMounted"
          :team-name="team.name"
          :team-start-date="team.firstMatchDate"
          :discontinued-rows="discontinuedTeamsStore.rows"
        />
      </div>
      <div v-show="activeTab === 'stats'" class="tab-panel">
        <TeamStats v-if="statsTabMounted" :team="team" />
      </div>
    </section>
  </div>
  <div v-else class="team">
    Team niet gevonden.
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router';
import useTeamsStore from '@/stores/teams';
import { computed, ref, watch } from 'vue'
import Dygraph from '@/components/Dygraph.vue'
import usePersistentStore from '@/stores/persistentStore'
import { getRatingColor } from '@/utils'
import TeamStats from '@/components/TeamStats.vue'
import { Team } from '@/types'
import useDiscontinuedTeamsStore from '@/stores/discontinuedTeams'
import FateComponent from '@/components/FateComponent.vue'
import TeamLineageGraph from '@/components/TeamLineageGraph.vue'

const route = useRoute();
const teamsStore = useTeamsStore();
const persistentStore = usePersistentStore();
const discontinuedTeamsStore = useDiscontinuedTeamsStore()
type TeamTab = 'graph' | 'origin' | 'stats'
const activeTab = ref<TeamTab>('graph')
const graphTabMounted = ref(true)
const originTabMounted = ref(false)
const statsTabMounted = ref(false)

teamsStore.fetchRankings()
discontinuedTeamsStore.fetchDiscontinuedTeams()

const teamName = computed(() => String(route.params.name))
const discontinuedTeam = computed(() => discontinuedTeamsStore.rows.find((row) => row.name === teamName.value))
const hasIncomingOriginEvents = computed(() => {
  return discontinuedTeamsStore.rows.some((row) => {
    if (row.fateType !== 'rename' && row.fateType !== 'merge_into' && row.fateType !== 'fuse_new') {
      return false
    }
    return resolveOriginTarget(row) === teamName.value
  })
})
const hasSimpleDiscontinuedLineage = computed(() => {
  const row = discontinuedTeam.value
  if (!row || row.fateType !== 'end') {
    return false
  }
  return !hasIncomingOriginEvents.value
})
const hasOriginTab = computed(() => {
  if (discontinuedTeam.value) {
    return !hasSimpleDiscontinuedLineage.value
  }
  return hasIncomingOriginEvents.value
})

function resolveOriginTarget(row: { fateType: string, fateTeams: string[] }): string | null {
  if (row.fateType === 'rename' || row.fateType === 'merge_into') {
    return row.fateTeams[0] ?? null
  }
  if (row.fateType === 'fuse_new') {
    return row.fateTeams[row.fateTeams.length - 1] ?? null
  }
  return null
}

function setActiveTab(tab: TeamTab) {
  activeTab.value = tab
  if (tab === 'origin') {
    originTabMounted.value = true
  }
  if (tab === 'stats') {
    statsTabMounted.value = true
  }
}

const team = computed(() => {
  const currentTeam = teamsStore.teamsByName.get(teamName.value)
  if (currentTeam) {
    return currentTeam
  }
  const row = discontinuedTeam.value
  if (!row) {
    return null
  }
  return new Team(
    row.name,
    row.lastRating ?? 0,
    row.lastMatchDate ?? new Date(0),
    0,
    row.place,
    350,
    0.06,
    5,
    row.firstMatchDate ?? row.lastMatchDate ?? new Date(0),
  )
})

watch(hasOriginTab, (available) => {
  if (available) {
    return
  }
  if (activeTab.value === 'origin') {
    activeTab.value = 'graph'
  }
})
</script>

<style scoped>
.team {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.team h1 {
  font-size: 2em;
  text-align: center;
  margin-bottom: 20px;
}

.team-info {
  display: flex;
  justify-content: center;
  gap: 20px;
  flex-wrap: wrap;
}

.info-item {
  padding: 8px 16px;
  background-color: var(--color-background-soft);
  border-radius: 4px;
  font-size: 1.1em;
}

.fate-item {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex-wrap: wrap;
}

.team-tabs {
  margin-top: 1rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 0.45rem;
}

.team-tab {
  border: 1px solid var(--color-border);
  background: var(--color-background-soft);
  color: var(--color-text);
  padding: 0.4rem 0.7rem;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;
}

.team-tab.active {
  border-color: var(--color-border-hover);
  background: color-mix(in srgb, var(--color-background-soft) 66%, var(--color-border) 34%);
}

.tab-panels {
  margin-top: 0.8rem;
}

.tab-panel {
  min-height: 1px;
}
</style>
