import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
declare module 'vue-router' {
  interface RouteMeta {
    hidesSidebar?: boolean
  }
}
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior() {
    return { top: 0 }
  },
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/uitleg',
      name: 'uitleg',
      component: () => import('../views/AboutView.vue'),
      meta: { hidesSidebar: true },
    },
    {
      path: '/grafiek',
      name: 'grafiek',
      component: () => import('../views/MegaGraph.vue'),
    },
    {
      path: '/matchup/:name1...:name2',
      name: 'matchup',
      props: true,
      component: () => import('../views/MatchupView.vue'),
      meta: { hidesSidebar: true },
    },
    {
      path: '/team/:name',
      name: 'team',
      component: () => import('../views/TeamView.vue'),
      meta: { hidesSidebar: true },
    },
    {
      path: '/competitie/:season',
      name: 'poules',
      component: () => import('../views/PoulesView.vue'),
      props: true,
      meta: { hidesSidebar: true },
    },
    {
      path: '/poule/:season/:name',
      name: 'poule',
      component: () => import('../views/PouleView.vue'),
      props: true,
      meta: { hidesSidebar: true },
    },
    {
      path: '/simulator',
      name: 'simulator',
      component: () => import('../views/SimulatorView.vue'),
      meta: { hidesSidebar: true },
    },
    {
      path: '/changelog',
      name: 'changelog',
      component: () => import('../views/ChangelogView.vue')
    },
    {
      path: '/data',
      name: 'data',
      component: () => import('../views/DataView.vue')
    },
    {
      path: '/opgeheven-teams',
      name: 'opgeheven-teams',
      component: () => import('../views/DiscontinuedTeamsView.vue'),
      meta: { hidesSidebar: true },
    },
    {
      path: '/verdwenen-teams',
      redirect: '/opgeheven-teams',
    },
  ],
})

export default router
