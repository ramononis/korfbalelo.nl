<script setup lang="ts">
import usePoulesStore from '@/stores/poules'
import useTeamsStore from '@/stores/teams'
import { computed, onMounted, ref, watch } from 'vue'
import { getRatingColor } from '@/utils'
import { ACTIVE_SEASONS } from '@/season'
import { getWithdrawalReason } from '@/simulator/SeasonTransitionRules'
import TeamComponent from '@/components/TeamComponent.vue'
const props = defineProps<{
  season: string // zaal or veld
}>()
const teamsStore = useTeamsStore()
const poulesStore = usePoulesStore()
const orderByRating = ref<boolean>(false)

function getAverageRating(teams: string[]) {
  const ratings = teams.map(getTeamRating).filter((rating): rating is number => rating !== null)
  return ratings.length === 0
    ? 0
    : ratings.reduce((sum, rating) => sum + rating, 0) / ratings.length
}

const showRating = computed(() => ACTIVE_SEASONS.some((season) => season.seasonName === props.season))

function getTeamRating(teamName: string): number | null {
  return teamsStore.teamsByName.get(teamName)?.rating ?? null
}

function sortTeams(teams: string[]): string[] {
  if (!showRating.value) {
    return teams
  }
  return teams
    .map((teamName, index) => ({
      teamName,
      index,
      rating: getTeamRating(teamName),
    }))
    .sort((a, b) => {
      if (a.rating !== null && b.rating !== null && a.rating !== b.rating) {
        return b.rating - a.rating
      }
      if (a.rating === null && b.rating !== null) {
        return 1
      }
      if (a.rating !== null && b.rating === null) {
        return -1
      }
      return a.index - b.index
    })
    .map(({ teamName }) => teamName)
}

function sortPoules(poules: { [pouleName: string]: string[] }): [string[], string][] {
  return Object.entries(poules)
    .map(([pouleName, teams], index) => [sortTeams(teams), pouleName, index] as [string[], string, number])
    .sort(([teamsA, , indexA], [teamsB, , indexB]) =>
      orderByRating.value
        ? getAverageRating(teamsB) - getAverageRating(teamsA)
        : indexA - indexB
    )
    .map(([teams, pouleName]) => [teams, pouleName] as [string[], string])
}

function withdrawalReason(teamName: string) {
  return getWithdrawalReason(props.season, teamName)
}

// Watch the season prop for changes
watch(() => props.season, (newSeason, oldSeason) => {
  if (newSeason !== oldSeason) {
    poulesStore.fetchTierData(newSeason)
  }
})
onMounted(() => {
  teamsStore.fetchRankings()
  poulesStore.fetchTierData(props.season)
})
</script>

<template>
  <div class="poules">
    <h1 @click="orderByRating = !orderByRating">Poules</h1>
    <div v-for="(poules, tierName) in poulesStore.tiers.get(props.season)" :key="tierName" class="tier">
      <h2>{{ tierName }}</h2>
      <div class="tierpoules">
        <div v-for="[teams, poule] in sortPoules(poules)" :key="poule" class="poule">
          <router-link :to='"/poule/" + encodeURIComponent(season) + "/" + encodeURIComponent(poule)'>
            {{ poule }}
            <span class="rating" v-if="showRating" :style="{ color: getRatingColor(getAverageRating(teams)) }">
            {{ getAverageRating(teams).toFixed(0) }}
          </span>
          </router-link>
          <ul>
            <li v-for="teamName in teams" :key="teamName">
              <div class="team-line">
                <TeamComponent :team-name="teamName" :show-rating="showRating"/>
                <span
                  v-if="withdrawalReason(teamName)"
                  class="withdrawal-marker"
                  :title="withdrawalReason(teamName) ?? undefined"
                >*</span>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>


<style scoped>
.poules {
  padding: 10px;
}

.tier {
  margin-bottom: 20px;
}

.tierpoules {
  display: flex;
  flex-wrap: wrap;
}

.poule {
  display: inline-block;
  margin: 1%;
  background-color: var(--color-background-soft);
  color: #fff;
  border-radius: 8px;
  padding: 10px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.3);
  box-sizing: border-box;
  vertical-align: top; /* Ensure top alignment if inline-block is used */
}

.poule ul {
  list-style-type: none; /* Remove bullet points */
  padding-left: 0; /* Remove default padding */
}

.team-line {
  display: flex;
  align-items: baseline;
  gap: 0.2rem;
  width: 100%;
}

.team-line :deep(.team-container) {
  flex: 1 1 auto;
  min-width: 0;
}

.withdrawal-marker {
  color: #f0c24b;
  font-weight: 700;
  cursor: help;
  flex: 0 0 auto;
}

.poule > a {
  display: block;
  margin-bottom: 10px;
  text-decoration: none;
}

.poule h2 {
  margin-top: 0;
}

.router-link {
  color: #1e90ff; /* Link color that stands out in dark theme */
  text-decoration: none;
}

.poule a {
  display: flex;
  justify-content: space-between;
}

.name {
  font-weight: bold;
  flex-grow: 1;
}

.rating {
  margin-left: 10px;
}
</style>
