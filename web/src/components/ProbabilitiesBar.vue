<script setup lang="ts">

import { computed, ref } from 'vue'

const props = defineProps<{
  pChampion: number,
  pPromote: number,
  pRelegate: number,
}>()

const expand = ref(false)

const pStay = computed(() => 100 - props.pPromote - props.pRelegate)
const pOnlyPromote = computed(() => Math.max(0, props.pPromote - props.pChampion))
</script>

<template>
  <div class="probability-container">
    <div class="probability-bar-line">
      <div class="probability-bar">
        <div class="champion" :title="pChampion + '%'" :style="{ width: pChampion + '%' }"></div>
        <div class="promote" :title="pPromote + '%'" :style="{ width: pOnlyPromote + '%' }"></div>
        <div class="relegate" :title="pRelegate + '%'" :style="{ width: pRelegate + '%' }"></div>
      </div>
      <button class="expand-button" @click="expand = !expand">
        <span v-if="expand">&#x25B2;</span> <!-- Upwards filled arrowhead -->
        <span v-else>&#x25BC;</span> <!-- Downwards filled arrowhead -->
      </button>
    </div>
    <div v-if="expand" class="probability-values">
      <div v-if="pChampion !== 0">
        Kampioen: {{ pChampion.toFixed(5) }}%
      </div>
      <div v-if="pPromote !== 0 && pPromote !== pChampion">
        Promotie: {{ pPromote.toFixed(5) }}%
      </div>
      <div v-if="pStay !== 0">
        Handhaving: {{ pStay.toFixed(5) }}%
      </div>
      <div v-if="pRelegate !== 0">
        Degradatie: {{ pRelegate.toFixed(5) }}%
      </div>
    </div>
  </div>

</template>

<style scoped>

.probability-container {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.probability-bar-line {
  display: flex;
  width: 100%;
  align-items: center;
}
.probability-bar {
  display: flex;
  height: 20px;
  width: calc(100% - 40px);
  margin-left: 10px;
  background-color: white;
  border-radius: 4px;
  overflow: hidden;
  position: relative;
}

.probability-bar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: repeating-linear-gradient(
    to right,
    transparent,
    transparent 4.7%,
    rgba(0, 0, 0, 1) 4.7%,
    rgba(0, 0, 0, 1) 5%
  );
  z-index: 1;
}

.probability-bar div {
  height: 100%;
  z-index: 0;
}

.champion {
  background-color: gold;
  flex-shrink: 0;
}

.promote {
  background-color: green;
  flex-shrink: 0;
}

.relegate {
  position: absolute;
  right: 0;
  background-color: red;
  flex-shrink: 0;
}

.expand-button {
  background-color: #2c2c2c00;
  color: #e0e0e0;
  border: none;
  cursor: pointer;
  margin: 0 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 15px;
  height: 30px;
}

.expand-button:hover {
  background-color: #333;
}
</style>
