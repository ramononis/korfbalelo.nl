<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router';
import Ranking from '@/components/Ranking.vue';
import { ACTIVE_SEASONS, type SeasonConfig } from '@/season'

const route = useRoute();
const isGrafiekRoute = computed(() => {
  return route.name === 'grafiek';
});
const showArchief = ref(false);
// Close dropdown when clicking outside
const handleClickOutside = (event: MouseEvent) => {
  const dropdownBtn = event.target as HTMLElement;
  if (!dropdownBtn.closest('.dropdown')) {
    showArchief.value = false;
  }
};

// Add and remove event listener
import { onMounted, onUnmounted } from 'vue';
onMounted(() => {
  document.addEventListener('click', handleClickOutside);
});
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
});

const formatSeasonLabel = (season: SeasonConfig): string => {
  return `20${season.seasonCode.slice(0, 2)}/20${season.seasonCode.slice(2, 4)} ${season.mode}`
}

</script>

<template>
  <template v-if="isGrafiekRoute">
    <router-view/>
  </template>
  <div v-else class="app-container">
    <!-- Header -->
    <header class="app-header">
      <h1>Korfbal Elo</h1>
      <nav class="nav-tabs">
        <router-link to="/" exact-active-class="active">Home</router-link>
        <router-link to="/uitleg" exact-active-class="active">Uitleg</router-link>
        <router-link
          v-for="season in ACTIVE_SEASONS"
          :key="season.seasonName"
          :to="`/competitie/${season.seasonName}`"
          exact-active-class="active"
        >
          {{ formatSeasonLabel(season) }}
        </router-link>
        <a class="dropdown">
          <button class="dropdown-btn" @click="showArchief = !showArchief">
            Archief ▼
          </button>
          <div class="dropdown-content" v-show="showArchief" @click="showArchief = false">
            <router-link to="/competitie/zaal2526" exact-active-class="active">2025/2026 zaal</router-link>
            <router-link to="/competitie/veld2526nj" exact-active-class="active">2025 veld najaar</router-link>
            <router-link to="/competitie/veld2425vj" exact-active-class="active">2025 veld voorjaar </router-link>
            <router-link to="/competitie/zaal2425" exact-active-class="active">2024/2025 zaal</router-link>
            <router-link to="/competitie/veld2425nj" exact-active-class="active">2024 veld najaar </router-link>
          </div>
        </a>
        <router-link to="/grafiek" exact-active-class="active">Mega grafiek (pas op, zeer traag)</router-link>
        <router-link to="/data" exact-active-class="active">Data</router-link>
        <router-link to="/changelog" exact-active-class="active">Changelog</router-link>
      </nav>
    </header>

    <div class="content-wrapper">
      <!-- Sidebar -->
      <aside class="sidebar" :class="{ 'hides-sidebar': route.meta.hidesSidebar }">
        <ranking />
      </aside>

      <!-- Main Content -->
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style>
.app-container {
  display: flex;
  flex-direction: column;
  align-content: center;
  min-height: 100vh;
}

.app-header {
  background-color: var(--color-background-mute);
  padding: 10px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  position: relative; /* Add this */
  z-index: 100; /* High z-index to ensure it's above other content */
}

.app-header h1 {
  margin: 0;
  font-size: 3.0em;
}

.nav-tabs a {
  margin-right: 15px;
  text-decoration: none;
  white-space: nowrap; /* Prevent line breaks within links */
}

.nav-tabs a.active {
  text-decoration: underline;
}

.content-wrapper {
  display: flex;
  flex: 1;
}

.sidebar {
  width: 250px;
  background-color: var(--color-background-mute);
  overflow-y: auto; /* Enable vertical scrolling */
  max-height: calc(100vh - 2rem); /* Subtract header height + padding */
  position: sticky; /* Keep sidebar in place while scrolling */
  top: 0;
}

@media (max-width: 1280px) {
  .sidebar {
    max-height: calc(100vh); /* Subtract header height + padding */
  }
}

.main-content {
  flex: 1;
  padding: 20px;
}

.nav-tabs {
  display: flex;
  flex-wrap: wrap;
  overflow-x: auto;
  padding-bottom: 5px;
  position: relative;
  z-index: 10;
}

.dropdown {
  position: relative;
  display: inline-block;
}

.dropdown-btn {
  background-color: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
  color: var(--color-text);
  font-size: inherit;
}

.dropdown-content {
  position: absolute;
  top: 100%;
  left: 0;
  background-color: var(--color-background);
  min-width: 160px;
  box-shadow: 0 8px 16px rgba(0,0,0,0.2);
  z-index: 200; /* Very high z-index */
  display: flex;
  flex-direction: column;
  padding: 0.5rem 0;
  border: 1px solid var(--color-border);
}

.dropdown-content a {
  padding: 0.5rem 1rem;
  margin: 0;
  text-decoration: none;
  color: var(--color-text);
  white-space: nowrap;
}

.dropdown-content a:hover {
  background-color: var(--color-background-mute);
}

.dropdown-content a.active {
  text-decoration: underline;
}

/* Prevent horizontal scrolling */
.nav-tabs {
  overflow-x: visible;
}

</style>
