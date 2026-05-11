<script setup lang="ts">
defineOptions({
  name: 'TxFormula',
})

defineProps<{
  f: string
}>()
</script>

<template>
  <vue-mathjax :formula="'$' + f + '$'"/>
</template>
