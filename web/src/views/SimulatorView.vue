<script setup lang="ts">
import usePersistentStore from '@/stores/persistentStore'
import { toFixtureString } from '@/types'
import { fetchMetaData, metaData, type SimulatorParams, type SimulatorResult } from '@/simulator/SeasonSimulator'
import { computed, onMounted, onUnmounted, ref, toRaw, watch } from 'vue'
import ProbabilitiesBar from '@/components/ProbabilitiesBar.vue'
import SimulationWorker from '@/simulator/SimulationWorker?worker'
import usePoulesStore from '@/stores/poules.ts'
import useTeamsStore from '@/stores/teams'
import axios from 'axios'

const teamsStore = useTeamsStore()
const persistentStore = usePersistentStore()
const results = ref<SimulatorResult>(new Map())
const nSimulations = computed(() => [...results.value.values()].reduce((sum, p) => sum + p.n, 0))

const speed = ref(0)
const running = ref(false)
const workers = new Array<Worker>()
const numWorkers = ref((navigator.hardwareConcurrency - 2) || 1);
const maxWorkers = navigator.hardwareConcurrency
let startTime = 0
let n = 0;
const maxSpeed = ref(0)

watch(numWorkers, () => {
  if (running.value) {
    if (workers.length > numWorkers.value) {
      while (workers.length > numWorkers.value) {
        stopWorker()
      }
    } else {
      while (workers.length < numWorkers.value) {
        spawnWorker()
      }
    }
  }
})

function stopWorker() {
  workers.pop()?.terminate()
}

function spawnWorker() {
  const worker = new SimulationWorker()

  worker.onmessage = (e) => {

    const result = e.data

    // Process the result
    if (results.value.size === 0) {
      const range = persistentStore.match!.special ? [-1, 1] : persistentStore.range
      for (const m of range) {
        results.value.set(m, {
          pChampion: 0,
          pPromote: 0,
          pRelegate: 0,
          n: 0
        })
      }
    }

    for (const [m, p] of result) {
      const res = results.value.get(m)!
      res.n += p.n
      n += p.n
      res.pChampion += p.pChampion
      res.pPromote += p.pPromote
      res.pRelegate += p.pRelegate
    }

    if (performance.now() - startTime > 500) {
      const newSpeed = (n / (performance.now() - startTime)) * 1000
      speed.value = speed.value * 0.9 + newSpeed * 0.1
      maxSpeed.value = Math.max(maxSpeed.value, speed.value)
      startTime = performance.now()
      n = 0
    }

    if (running.value) {
      worker.postMessage({ type: 'next' })
    } else {
      worker.terminate()
    }
  }

  workers.push(worker)
  worker.postMessage({ type: 'start', payload: simulationParams })

}

let simulationParams: SimulatorParams | null = null


async function doSimulate() {
  running.value = true
  n = 0
  startTime = performance.now()
  await usePoulesStore().fetchTierData(persistentStore.season)
  await fetchMetaData()
  const pouleData = new Map<string, string>()
  for (const pouleName of persistentStore.relevantTeams!.relevantPoules) {
    const data = await axios.get(`/csv/${persistentStore.season}/${pouleName}.csv`, {
      transformResponse: x => x
    })
    pouleData.set(pouleName, data.data)
  }

  simulationParams = {
    season: persistentStore.season,
    team: persistentStore.simulationTeam!,
    relevantTeams: persistentStore.relevantTeams!,
    match: toRaw(persistentStore.match!),
    range: persistentStore.match!.special ? [-1, 1] : persistentStore.range!,
    teamsByName: new Map([...teamsStore.teamsByName.entries()].map(([key, value]) => [key, toRaw(value)])),
    meta: metaData,
    pouleData
  }

  startWorkers()
}

function handleVisibilityChange() {
  if (!running.value) return;
  if (document.visibilityState === 'hidden') {
    // Pause or stop the workers
    stopWorkers();
  } else if (document.visibilityState === 'visible') {
    // Resume or start the workers
    startWorkers();
  }
}

function startWorkers() {
  for (let i = 0; i < numWorkers.value; i++) {
    spawnWorker()
  }

}

function stopWorkers() {
  workers.forEach((worker) => worker.terminate())
  workers.length = 0
}

// Mount the visibility change listener
onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange);
});


function stopSimulate() {
  running.value = false
  stopWorkers()
}

onUnmounted(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  stopSimulate()
});

</script>

<template>
  <div class="top-bar">

    <div>
      <h1>Simulator</h1>
      <div>Team: {{ persistentStore.simulationTeam }}</div>
      <br>
      <div>Wedstrijd: {{ toFixtureString(persistentStore.match!) }}</div>
      <br>
      <label>Marge interval:</label><br>
      <input type="number" v-model="persistentStore.scoreInterval.low" />
      <input type="number" v-model="persistentStore.scoreInterval.high" />
      <br>
      <span>Aantal cores: </span>
      <input type="range" min="1" :max="maxWorkers" v-model="numWorkers" />
      <span>{{ numWorkers }}</span>
      <br>
      <button v-if="!running" @click="doSimulate">Start simulatie</button>
      <button v-else @click="stopSimulate">Stop</button>
      <button @click="results.clear()">Reset</button>
    </div>
  </div>
  <div><code>{{ nSimulations.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",") }} simulaties ({{ speed.toFixed(0).replace(/\B(?=(\d{3})+(?!\d))/g, ",") }} / s, max {{ maxSpeed.toFixed(0).replace(/\B(?=(\d{3})+(?!\d))/g, ",")}} / s)</code></div>

  <div>
    <table>
      <tr v-for="[m, p] of [...results].reverse()" :key="m">
        <td>{{ m }}</td>
        <td class="probabilities">
          <ProbabilitiesBar :pChampion="p.pChampion / p.n * 100" :pPromote="p.pPromote / p.n * 100"
                            :pRelegate="p.pRelegate / p.n * 100" />
        </td>
      </tr>
    </table>
  </div>
</template>

<style scoped>
.probabilities {
  width: 100%;
}

.top-bar {
  display: flex;
}
</style>
