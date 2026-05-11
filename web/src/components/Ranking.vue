<template>
  <div class="ranking">
    <h2>Ranking</h2>
    <router-link class="discontinued-link" to="/opgeheven-teams">Opgeheven teams</router-link>
    <ul>
      <li v-for="(team, index) in teamsStore.teams" :key="team.name" class="rank-item">
        <span class="rank small">{{ index + 1 }}</span>
        <TeamComponent :teamName="team.name" show-diff/>
      </li>
     </ul>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import useTeamsStore from '@/stores/teams';
import TeamComponent from '@/components/TeamComponent.vue';

defineOptions({
  name: 'TeamRanking',
})

const teamsStore = useTeamsStore();

onMounted(() => {
  teamsStore.fetchRankings();
});
</script>

<style scoped>
.ranking {
  padding: 10px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.ranking h2 {
  margin-bottom: 4px;
  text-align: center;
  margin-top: 0;
}

.discontinued-link {
  display: block;
  text-align: center;
  margin-bottom: 8px;
  font-size: 0.9em;
}

ul {
  list-style: none;
  padding: 0;
  margin: 0;
  overflow-y: auto;
  flex: 1;
}

.rank-item {
  display: flex;
  align-items: center;
  padding: 5px 0;
}

.rank {
  width: 1.5em;
  margin-right: 10px;
}

.team-container {
  flex-grow: 1; /* Allow TeamComponent to take up remaining space */
  display: flex; /* Ensure flex layout for internal alignment */
}
</style>
