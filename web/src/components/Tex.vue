<script setup lang="ts">
defineOptions({
  name: 'TexFormula',
})

defineProps<{
  f: string
}>()
</script>

<template>
  <vue-mathjax class="big" :formula="'$$' + f + '$$'"/>
</template>

<style scoped>
.big {
  font-size: 1.5em
}
</style>
