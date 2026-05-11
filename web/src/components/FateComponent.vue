<script setup lang="ts">
import { computed } from 'vue'
import TeamComponent from '@/components/TeamComponent.vue'

const props = withDefaults(defineProps<{
  fate: string
  fateType?: string
  fateTeams?: string[]
  strikeDiscontinued?: boolean
}>(), {
  fateType: '',
  fateTeams: () => [],
  strikeDiscontinued: true,
})

const fusedSources = computed(() => props.fateType === 'fuse_new' ? props.fateTeams.slice(0, -1) : [])
const fusedTarget = computed(() => {
  if (props.fateType !== 'fuse_new' || props.fateTeams.length === 0) {
    return null
  }
  return props.fateTeams[props.fateTeams.length - 1] ?? null
})
</script>

<template>
  <span v-if="fateType === 'end'">Gestopt</span>
  <template v-else-if="fateType === 'rename' && fateTeams.length > 0">
    <span>Hernoemd naar </span>
    <TeamComponent :team-name="fateTeams[0]!" :show-rating="false" :strike-discontinued="strikeDiscontinued" inline />
  </template>
  <template v-else-if="fateType === 'merge_into' && fateTeams.length > 0">
    <span>Opgegaan in </span>
    <TeamComponent :team-name="fateTeams[0]!" :show-rating="false" :strike-discontinued="strikeDiscontinued" inline />
  </template>
  <template v-else-if="fateType === 'fuse_new' && fateTeams.length > 1 && fusedTarget">
    <span>Gefuseerd met </span>
    <template v-for="(teamName, index) in fusedSources" :key="`${teamName}-${index}`">
      <span v-if="index > 0">{{ index === fusedSources.length - 1 ? ' en ' : ', ' }}</span>
      <TeamComponent :team-name="teamName" :show-rating="false" :strike-discontinued="strikeDiscontinued" inline />
    </template>
    <span> tot </span>
    <TeamComponent :team-name="fusedTarget" :show-rating="false" :strike-discontinued="strikeDiscontinued" inline />
  </template>
  <span v-else>{{ fate }}</span>
</template>
