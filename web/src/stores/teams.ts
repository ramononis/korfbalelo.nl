import { defineStore } from 'pinia';
import Papa from 'papaparse';
import { Team } from '@/types';

export default defineStore('teams', {
  state: ()  => {
    return {
      teams: new Array<Team>(),
      allTeams: new Array<Team>(),
      activeTeamNames: new Array<string>(),
      loading: false,
    }
  },
  getters: {
    teamsByName(state): Map<string, Team> {
      return state.allTeams.reduce((map, team) => {
        map.set(team.name, team);
        return map;
      }, new Map<string, Team>());
    },
    loaded(state) {
      return state.allTeams.length > 0;
    },
    isActiveTeam(state) {
      const activeNames = new Set(state.activeTeamNames)
      return (teamName: string) => activeNames.has(teamName)
    },
  },
  actions: {
    fetchRankings() {
      if (this.loaded || this.loading) {
        return;
      }
      this.loading = true
      try {
        Papa.parse('/ranking.csv', {
          download: true,
          dynamicTyping: true,
          delimiter: ',',
          skipEmptyLines: true,
          complete: (results) => {
            const data = results.data as unknown[][]
            const oneYearAgo = new Date();
            oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);

            const parsedTeams = data
              .map((row) => {
                const [name, rating, lastDateValue, firstDateValue, seasonRatingDiff, place, rd, rv, averageScore] = row
                if (typeof name !== 'string' || typeof lastDateValue !== 'string' || typeof place !== 'string') {
                  return null
                }
                const lastMatchDate = new Date(lastDateValue);
                if (Number.isNaN(lastMatchDate.getTime())) {
                  return null
                }
                const parsedFirstMatchDate = typeof firstDateValue === 'string' && firstDateValue
                  ? new Date(firstDateValue)
                  : null
                const firstMatchDate = parsedFirstMatchDate && !Number.isNaN(parsedFirstMatchDate.getTime())
                  ? parsedFirstMatchDate
                  : lastMatchDate
                return new Team(
                  name,
                  Number(rating),
                  lastMatchDate,
                  Number(seasonRatingDiff),
                  place,
                  Number(rd),
                  Number(rv),
                  Number(averageScore),
                  firstMatchDate,
                );
              })
              .filter((team): team is Team => !!team)

            this.activeTeamNames = parsedTeams.map((team) => team.name)
            this.allTeams = parsedTeams
            this.teams = parsedTeams
              .filter((team) => team.lastMatchDate >= oneYearAgo)
              .sort((a, b) => b.rating - a.rating)
            this.loading = false
          },
          error: (error) => {
            this.loading = false
            console.error('Error parsing ranking.csv:', error)
          }
        });
      } catch (error) {
        this.loading = false
        console.error('Error fetching ranking.csv:', error);
      }
    }
  }
});
