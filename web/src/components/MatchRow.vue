<script setup lang="ts">
import type { MatchFixture } from '@/types'
import { formattedDiff } from '@/utils'
import TeamComponent from '@/components/TeamComponent.vue'
import { computed } from 'vue'

export interface Emits {
  simulate: [match: MatchFixture]
}
const props = withDefaults(defineProps<{
  match: MatchFixture,
  withResult?: boolean,
  withSimulation?: boolean,
  showDate?: boolean,
}>(), {
  withResult: true,
  withSimulation: true,
  showDate: false,
})

const emit = defineEmits<Emits>()

const homeWinner = computed(() => props.withResult && props.match.homeScore > props.match.awayScore)
const awayWinner = computed(() => props.withResult && props.match.awayScore > props.match.homeScore)
const draw = computed(() => props.withResult && props.match.homeScore === props.match.awayScore)

</script>
<template>
  <tr>
    <td v-if="showDate" class="match-date">{{ match.date }}</td>
    <td class="no-pad-right" :class="{ winner: homeWinner, draw: draw, loser: awayWinner }">
      <TeamComponent :team-name="match.home" :rating="match.homeRating.toFixed(0)"/>
    </td>
    <td v-if="withResult" class="small no-pad-left">{{ formattedDiff(match.homeDiff) }}</td>
    <td class="no-pad-right" :class="{ winner: awayWinner, draw: draw, loser: homeWinner }">
      <TeamComponent :team-name="match.away" :rating="match.awayRating.toFixed(0)"/>
    </td>
    <td v-if="withResult" class="small no-pad-left">{{ formattedDiff(match.awayDiff) }}</td>
    <td>
      <router-link :to="`/matchup/${encodeURIComponent(match.home)}...${encodeURIComponent(match.away)}`">
        {{ formattedDiff(match.homeRating - match.awayRating) }}
      </router-link>
    </td>
    <td v-if="withResult">{{ match.homeScore }} - {{ match.awayScore }}</td>
    <td>{{ (match.pHome * 100).toFixed(0) }}%</td>
    <td>{{ (match.pDraw * 100).toFixed(0) }}%</td>
    <td>{{ (match.pAway * 100).toFixed(0) }}%</td>
    <td class="no-padding">
      {{ match.guessHome }} - {{ match.guessAway }}
      <button
        v-if="withSimulation"
        @click="emit('simulate', match)">
        Simuleer
      </button>
    </td>
  </tr>
</template>

<style scoped>
.winner, .winner + * {
  background-color: var(--color-background-winner);
}

.draw, .draw + * {
  background-color: var(--color-background-draw);
}
.loser, .loser + * {
  background-color: var(--color-background-loser);
}

.match-date {
  font-weight: 700;
  white-space: nowrap;
}
</style>
