<template>
  <div class="about-item">
    <h3 class="header">
      <i>
        "<slot name="header"/>"
      </i>
    </h3>
    <div class="content">
      <slot/>
    </div>
  </div>
</template>

<style scoped>
.about-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  margin: 1rem;
  padding: 1rem;
  background: var(--color-background-mute);
  border-radius: 0.5rem;
}

.header {
  margin-bottom: 1rem;
  text-align: center;
}

.content {
  text-align: center;
}
</style>
