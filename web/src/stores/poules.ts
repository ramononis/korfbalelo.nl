
import { defineStore } from 'pinia';
import axios from 'axios'

interface TiersData {
  [tierName: string]: {
    [pouleName: string]: string[]
  }
}

export default defineStore('poules', {
  state: () => ({
    tiers: new Map<string, TiersData>(),
  }),
  actions: {
    async fetchTierData(name: string): Promise<void> {
      // AI generated: generated season data can change while the frontend stays open.
      await axios.get(`/${name}.json`).then((response) => {
        this.tiers.set(name, response.data);
      });
    },
  },
});
