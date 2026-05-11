
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
      if (!this.tiers.has(name)) {
        await axios.get(`/${name}.json`).then((response) => {
          this.tiers.set(name, response.data);
        });
      }
    },
  },
});
