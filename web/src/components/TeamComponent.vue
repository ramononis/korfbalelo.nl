<template>
  <div class="team-container" :class="{ 'inline-container': inline }">
    <router-link v-if="!isCurrentTeamPage" class="teamlink" :class="{ 'inline-link': inline }" :to="teamLink">
      <span
        class="team-name"
        :class="{ simulation: isSimulation, discontinued: shouldStrikeThrough }"
      >
        {{ teamName }}
      </span>
      <span class="rating-section" v-if="showRating">
        <span class="team-rating" :style="{ color: getRatingColor(effectiveRating) }">{{ effectiveRating }}</span>
        <span v-if="showDiff" class="rating-diff small flex">
         <span title="Verschil t.o.v. begin van het seizoen">{{ effectiveRatingDiff }}</span>
        </span>
      </span>
    </router-link>
    <div v-else class="teamlink current-team" :class="{ 'inline-link': inline }" aria-current="page">
      <span
        class="team-name"
        :class="{ simulation: isSimulation, discontinued: shouldStrikeThrough }"
      >
        {{ teamName }}
      </span>
      <span class="rating-section" v-if="showRating">
        <span class="team-rating" :style="{ color: getRatingColor(effectiveRating) }">{{ effectiveRating }}</span>
        <span v-if="showDiff" class="rating-diff small flex">
          <span title="Verschil t.o.v. begin van het seizoen">{{ effectiveRatingDiff }}</span>
        </span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getRatingColor } from '@/utils'
import useTeamsStore from '@/stores/teams'
import usePersistentStore from '@/stores/persistentStore'
import useDiscontinuedTeamsStore from '@/stores/discontinuedTeams'

const persistentStore = usePersistentStore()

const props = withDefaults(defineProps<{
  teamName: string,
  rating?: string | number,
  ratingDiff?: string,
  showDiff?: boolean,
  showRating?: boolean,
  strikeDiscontinued?: boolean,
  inline?: boolean,
}>(), {
  showDiff: false, // Default value for showDiff
  showRating: true,
  strikeDiscontinued: true,
  inline: false,
})

const isSimulation = computed(() => persistentStore.simulationTeam === props.teamName)
const route = useRoute()

const teamsStore = useTeamsStore()
const discontinuedTeamsStore = useDiscontinuedTeamsStore()

onMounted(() => {
  teamsStore.fetchRankings()
  discontinuedTeamsStore.fetchDiscontinuedTeams()
})

// Get rating and ratingDiff from teamsStore if not provided
const effectiveRating = computed(() => props.rating ?? teamsStore.teamsByName.get(props.teamName)?.formattedRating ?? 0)
const effectiveRatingDiff = computed(() => props.ratingDiff ?? teamsStore.teamsByName.get(props.teamName)?.formattedRatingDiff ?? '')
const shouldStrikeThrough = computed(() => {
  if (!props.strikeDiscontinued) {
    return false
  }
  if (!discontinuedTeamsStore.loaded) {
    return false
  }
  if (teamsStore.activeTeamNames.length === 0) {
    return false
  }
  return !teamsStore.isActiveTeam(props.teamName) && discontinuedTeamsStore.discontinuedNameSet.has(props.teamName)
})

const teamLink = computed(() => `/team/${encodeURIComponent(props.teamName)}`)
const isCurrentTeamPage = computed(() => {
  if (route.name !== 'team') {
    return false
  }
  const routeNameParam = route.params.name
  const currentTeamName = typeof routeNameParam === 'string' ? routeNameParam : routeNameParam?.[0]
  return currentTeamName === props.teamName
})
</script>

<style scoped>
.teamlink {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.inline-link {
  width: auto;
  justify-content: flex-start;
}

.current-team {
  cursor: default;
  text-decoration: none;
}

.team-container {
  display: flex;
  align-items: center;
}

.inline-container {
  display: inline-flex;
}

.team-name {
  font-weight: bold;
  margin-right: 8px;
}

.rating-section {
  display: flex;
  align-items: center;
}

.team-rating {
  text-align: right;
}

.rating-diff {
  color: gray;
  min-width: 2em;
  display: flex;
}

.rating-diff > span {
  margin-left: auto;
}

.simulation {
  color: darkgoldenrod;
}

.discontinued {
  text-decoration: line-through;
}
</style>
