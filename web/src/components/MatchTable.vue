<script setup lang="ts">
import MatchRow from '@/components/MatchRow.vue'
import type { MatchFixture } from '@/types'
import { computed } from 'vue'

export interface Emits {
  simulate: [match: MatchFixture]
}
const emit = defineEmits<Emits>()

const props = withDefaults(defineProps<{
  matches?: MatchFixture[],
  withResults?: boolean,
  withSimulation?: boolean,
  sortByDate?: boolean,
}>(), {
  withResults: false,
  withSimulation: false,
  sortByDate: true,
})

const groupedMatchesByDate = computed(() => {
  if (!props.matches) return [];

  const grouped: Record<string, MatchFixture[]> = {}

  // Group fixtures by date
  props.matches.forEach((match) => {
    const key = match.date
    if (!grouped[key]) {
      grouped[key] = []
    }
    grouped[key]!.push(match)
  });

  // Convert the object into an array that sorts the dates in chronological order
  const result = Object.entries(grouped);
  if (props.sortByDate) {
    result.sort((a, b) => new Date(a[0]).getTime() - new Date(b[0]).getTime());
  }
  if (props.withResults) {
    result.reverse()

  }
  return result
});

</script>
<template>
  <table>
    <thead>
    <tr>
      <th>Thuis</th>
      <th v-if="props.withResults">±</th>
      <th>Uit</th>
      <th v-if="props.withResults">±</th>
      <th>ΔRating</th>
      <th v-if="props.withResults">Uitslag</th>
      <th>P(Thuis)</th>
      <th>P(Gelijk)</th>
      <th>P(Uit)</th>
      <th>Voorspelling</th>
    </tr>
    </thead>
    <tbody>
    <template v-for="[date, matches] in groupedMatchesByDate" :key="date">
      <td>{{ date }}
      </td>
      <MatchRow v-for="(match, mIndex) in matches" :key="mIndex"
                :match="match"
                :with-result="withResults"
                :with-simulation="withSimulation"
                @simulate="emit('simulate', match)"
      />
    </template>
    </tbody>
  </table>

</template>
