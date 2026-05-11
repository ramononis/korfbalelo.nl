import { defineStore } from 'pinia'
import Papa from 'papaparse'

export interface DiscontinuedTeamRow {
  name: string
  place: string
  lastEventDate: Date
  lastMatchDate: Date | null
  firstMatchDate: Date | null
  topRating: number | null
  lastRating: number | null
  matches: number
  fate: string
  fateType: string
  fateTeams: string[]
}

interface DiscontinuedTeamCsvRow {
  name?: string
  place?: string
  lastEventDate?: string
  lastMatchDate?: string
  firstMatchDate?: string
  topRating?: number | string
  lastRating?: number | string
  matches?: number | string
  fate?: string
  fateType?: string
  fateTeams?: string
}

export default defineStore('discontinuedTeams', {
  state: () => ({
    rows: new Array<DiscontinuedTeamRow>(),
    loading: false,
  }),
  getters: {
    loaded(state) {
      return state.rows.length > 0
    },
    discontinuedNameSet(state): Set<string> {
      return new Set(state.rows.map((row) => row.name))
    },
  },
  actions: {
    fetchDiscontinuedTeams() {
      if (this.loading || this.loaded) {
        return
      }
      this.loading = true
      Papa.parse('/discontinued.csv', {
        download: true,
        delimiter: ',',
        skipEmptyLines: true,
        header: true,
        dynamicTyping: true,
        complete: (results) => {
          const parsed = (results.data as DiscontinuedTeamCsvRow[])
            .map((row) => {
              if (!row.name || !row.fate || !row.place || !row.fateType || !row.lastEventDate) {
                return null
              }
              const lastEventDate = new Date(row.lastEventDate)
              if (Number.isNaN(lastEventDate.getTime())) {
                return null
              }
              const firstMatchDate = row.firstMatchDate ? new Date(row.firstMatchDate) : null
              const lastMatchDate = row.lastMatchDate ? new Date(row.lastMatchDate) : null
              const topRating = row.topRating === '' || row.topRating == null ? null : Number(row.topRating)
              const lastRating = row.lastRating === '' || row.lastRating == null ? null : Number(row.lastRating)
              const fateTeams = typeof row.fateTeams === 'string' && row.fateTeams.length > 0
                ? row.fateTeams.split('|')
                : []
              return {
                name: row.name,
                place: row.place,
                lastEventDate,
                firstMatchDate,
                lastMatchDate,
                topRating,
                lastRating,
                matches: Number(row.matches ?? 0),
                fate: row.fate,
                fateType: row.fateType,
                fateTeams,
              } as DiscontinuedTeamRow
            })
            .filter((row): row is DiscontinuedTeamRow => !!row)
            .sort((a, b) => {
              const aTime = a.lastEventDate.getTime()
              const bTime = b.lastEventDate.getTime()
              if (aTime !== bTime) {
                return bTime - aTime
              }
              const aTarget = sortTargetTeam(a)
              const bTarget = sortTargetTeam(b)
              if (aTarget !== bTarget) {
                return aTarget.localeCompare(bTarget)
              }
              const aMatchTime = a.lastMatchDate?.getTime() ?? 0
              const bMatchTime = b.lastMatchDate?.getTime() ?? 0
              if (aMatchTime !== bMatchTime) {
                return bMatchTime - aMatchTime
              }
              return a.name.localeCompare(b.name)
            })
          this.rows = parsed
          this.loading = false
        },
        error: (error) => {
          this.loading = false
          console.error('Error loading discontinued.csv:', error)
        },
      })
    },
  },
})

function sortTargetTeam(row: DiscontinuedTeamRow): string {
  if (row.fateType === 'rename' || row.fateType === 'merge_into') {
    return row.fateTeams[0] ?? ''
  }
  if (row.fateType === 'fuse_new') {
    return row.fateTeams[row.fateTeams.length - 1] ?? ''
  }
  return ''
}
