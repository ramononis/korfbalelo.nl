import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import VueMathjax from 'vue-mathjax-next'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

console.log('WAT MOET DAT HIER? 👁️👁️')

createApp(App)
  .use(VueMathjax)
  .use(router)
  .use(
    createPinia()
      .use(piniaPluginPersistedstate)
  )
  .mount('#app')
