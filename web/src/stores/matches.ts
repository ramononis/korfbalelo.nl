import { defineStore } from 'pinia'
import axios from 'axios'

export const FIRST_YEAR = 1903

export default defineStore('matches', {
  state: () => ({
    matches: new Map<number, string>()
  }),
  actions: {
    async fetchMatches(year: number): Promise<void> {
      if (!this.matches.has(year)) {
        const response = await axios.get(`/matches/${year}.csv`)
        this.matches.set(year, response.data)
      }
    },
    clearCache() {
      this.matches.clear()
    }
  }
})
