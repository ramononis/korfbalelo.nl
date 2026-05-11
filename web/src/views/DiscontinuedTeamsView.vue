<script setup lang="ts">
import { computed, onMounted } from 'vue'
import useDiscontinuedTeamsStore from '@/stores/discontinuedTeams'
import TeamComponent from '@/components/TeamComponent.vue'
import FateComponent from '@/components/FateComponent.vue'
import { getRatingColor } from '@/utils'

const discontinuedTeamsStore = useDiscontinuedTeamsStore()

onMounted(() => {
  discontinuedTeamsStore.fetchDiscontinuedTeams()
})

const rows = computed(() => discontinuedTeamsStore.rows)

function formatDate(date: Date | null): string {
  return date ? date.toLocaleDateString('nl-NL') : '-'
}

function formatRating(rating: number | null): string {
  return rating == null ? '-' : rating.toFixed(0)
}

function ratingColor(rating: number | null): string | null {
  return rating == null ? null : getRatingColor(rating)
}
</script>

<template>
  <div class="discontinued-page">
    <h1>Opgeheven teams</h1>
    <p>
      Teams die niet meer bestaan in de ranking door stoppen, hernoemen of fusies.
    </p>

    <div class="table-wrapper">
      <table>
        <thead>
          <tr>
            <th>Naam</th>
            <th>Plaats</th>
            <th>Laatste wedstrijd</th>
            <th>Eerste wedstrijd</th>
            <th>Top rating</th>
            <th>Laatste rating</th>
            <th>Aantal wedstrijden</th>
            <th>Fate</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in rows" :key="`${row.name}-${row.fate}-${row.lastMatchDate?.toISOString() ?? 'none'}`">
            <td><TeamComponent :team-name="row.name" :show-rating="false" :strike-discontinued="false" /></td>
            <td>{{ row.place }}</td>
            <td>{{ formatDate(row.lastMatchDate) }}</td>
            <td>{{ formatDate(row.firstMatchDate) }}</td>
            <td :style="{ color: ratingColor(row.topRating) ?? undefined }">{{ formatRating(row.topRating) }}</td>
            <td :style="{ color: ratingColor(row.lastRating) ?? undefined }">{{ formatRating(row.lastRating) }}</td>
            <td>{{ row.matches }}</td>
            <td>
              <FateComponent :fate="row.fate" :fate-type="row.fateType" :fate-teams="row.fateTeams" :strike-discontinued="false" />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.discontinued-page {
  max-width: 1200px;
}

.table-wrapper {
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}

table {
  width: 100%;
  margin: 0;
  border-collapse: separate;
  border-spacing: 0;
  overflow: visible;
}

th, td {
  border-bottom: 1px solid var(--color-border);
  padding: 0.5rem 0.75rem;
  text-align: left;
}

th {
  background-color: var(--color-background-mute);
  position: sticky;
  top: 0;
  z-index: 2;
}
</style>
