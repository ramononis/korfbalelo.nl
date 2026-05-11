<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Ranking from '@/components/Ranking.vue'
import { ACTIVE_SEASONS, type SeasonConfig } from '@/season'

const route = useRoute()
const isGrafiekRoute = computed(() => {
  return route.name === 'grafiek'
})
const showArchief = ref(false)

const handleClickOutside = (event: MouseEvent) => {
  const target = event.target
  if (!(target instanceof Element) || !target.closest('.dropdown')) {
    showArchief.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

const formatSeasonLabel = (season: SeasonConfig): string => {
  return `20${season.seasonCode.slice(0, 2)}/20${season.seasonCode.slice(2, 4)} ${season.mode}`
}

</script>

<template>
  <template v-if="isGrafiekRoute">
    <router-view/>
  </template>
  <div v-else class="app-container">
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
        <div class="dropdown">
          <button type="button" class="dropdown-btn" @click="showArchief = !showArchief">
            Archief ▼
          </button>
          <div class="dropdown-content" v-show="showArchief" @click="showArchief = false">
            <router-link to="/competitie/zaal2526" exact-active-class="active">2025/2026 zaal</router-link>
            <router-link to="/competitie/veld2526nj" exact-active-class="active">2025 veld najaar</router-link>
            <router-link to="/competitie/veld2425vj" exact-active-class="active">2025 veld voorjaar </router-link>
            <router-link to="/competitie/zaal2425" exact-active-class="active">2024/2025 zaal</router-link>
            <router-link to="/competitie/veld2425nj" exact-active-class="active">2024 veld najaar </router-link>
          </div>
        </div>
        <router-link to="/grafiek" exact-active-class="active">Mega grafiek (pas op, zeer traag)</router-link>
        <router-link to="/data" exact-active-class="active">Data</router-link>
        <router-link to="/changelog" exact-active-class="active">Changelog</router-link>
        <a
          class="github-link"
          href="https://github.com/ramononis/korfbalelo.nl"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Bekijk de broncode op GitHub"
        >
          <svg class="github-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <path
              d="M12 2C6.48 2 2 6.59 2 12.25c0 4.53 2.87 8.37 6.85 9.73.5.1.68-.22.68-.49 0-.24-.01-1.05-.01-1.9-2.79.62-3.38-1.22-3.38-1.22-.46-1.19-1.11-1.51-1.11-1.51-.91-.64.07-.63.07-.63 1 .07 1.53 1.06 1.53 1.06.89 1.56 2.34 1.11 2.91.85.09-.66.35-1.11.63-1.37-2.23-.26-4.57-1.14-4.57-5.08 0-1.12.39-2.04 1.03-2.76-.1-.26-.45-1.31.1-2.72 0 0 .84-.28 2.75 1.05A9.3 9.3 0 0 1 12 6.91c.85 0 1.7.12 2.5.35 1.91-1.33 2.75-1.05 2.75-1.05.55 1.41.2 2.46.1 2.72.64.72 1.03 1.64 1.03 2.76 0 3.95-2.35 4.82-4.58 5.08.36.32.68.94.68 1.9 0 1.37-.01 2.47-.01 2.81 0 .27.18.59.69.49A10.17 10.17 0 0 0 22 12.25C22 6.59 17.52 2 12 2Z"
            />
          </svg>
        </a>
      </nav>
    </header>

    <div class="content-wrapper">
      <aside class="sidebar" :class="{ 'hides-sidebar': route.meta.hidesSidebar }">
        <ranking />
      </aside>

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
  position: relative;
  z-index: 100;
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
  overflow-y: auto;
  max-height: calc(100vh - 2rem);
  position: sticky;
  top: 0;
}

@media (max-width: 1280px) {
  .sidebar {
    max-height: 100vh;
  }
}

.main-content {
  flex: 1;
  padding: 20px;
}

.nav-tabs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  overflow-x: visible;
  padding-bottom: 5px;
  position: relative;
  z-index: 10;
}

.nav-tabs .github-link {
  align-items: center;
  display: inline-flex;
  justify-content: center;
  line-height: 1;
  margin-right: 0;
  padding: 4px;
}

.nav-tabs .github-icon {
  fill: currentColor;
  height: 1.6em;
  width: 1.6em;
}

.nav-tabs .github-link:hover {
  background-color: var(--color-background-soft);
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
  z-index: 200;
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

</style>
