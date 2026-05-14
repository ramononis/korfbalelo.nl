// AI-assisted: caches parsed match data client-side and persists it in IndexedDB by data version.
import { defineStore } from 'pinia'
import axios from 'axios'
import Papa from 'papaparse'
import { markRaw, ref, shallowRef } from 'vue'
import type { MatchFixture } from '@/types'

export const FIRST_YEAR = 1903

const DB_NAME = 'korfbalelo-match-cache'
const DB_VERSION = 1
const METADATA_STORE = 'metadata'
const MATCH_CHUNKS_STORE = 'matchChunks'
const ACTIVE_METADATA_KEY = 'active'

type MatchCsvRow = [
  string,
  string,
  string,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number,
  number
]

type MatchCacheMetadata = {
  key: string
  version: string
  maxId: number
  chunkIds: number[]
  matchCount: number
  earliestMatchDate: string
  latestMatchDate: string
  createdAt: string
}

type MatchCacheChunk = {
  id: number
  version: string
  matches: MatchFixture[]
}

export default defineStore('matches', () => {
  const matches = shallowRef<Map<number, string>>(markRaw(new Map()))
  const allMatches = shallowRef<MatchFixture[]>(markRaw([]))
  const matchesByTeam = shallowRef<Map<string, MatchFixture[]>>(markRaw(new Map()))
  const allMatchesLoaded = ref(false)
  const allMatchesLoading = ref(false)
  const earliestMatchDate = ref('')
  const latestMatchDate = ref('')
  const loadedMatchesVersion = ref('')

  const parsedMatchesByYear = new Map<number, MatchFixture[]>()
  let allMatchesPromise: Promise<void> | null = null
  let allMatchesPromiseKey = ''
  let loadedMaxId: number | null = null

  async function fetchMatches(year: number): Promise<void> {
    if (matches.value.has(year)) {
      return
    }
    const response = await axios.get(`/matches/${year}.csv`)
    const nextMatches = new Map(matches.value)
    nextMatches.set(year, response.data)
    matches.value = markRaw(nextMatches)
  }

  async function fetchParsedMatches(year: number): Promise<MatchFixture[]> {
    const cached = parsedMatchesByYear.get(year)
    if (cached) {
      return cached
    }

    await fetchMatches(year)
    const parsed = await parseMatchCsv(matches.value.get(year) ?? '')
    parsedMatchesByYear.set(year, markRaw(parsed))
    return parsed
  }

  async function ensureAllMatchesLoaded(maxId: number, matchesVersion = ''): Promise<void> {
    const cacheKey = `${matchesVersion}:${maxId}`
    if (allMatchesLoaded.value && loadedMatchesVersion.value === matchesVersion && loadedMaxId === maxId) {
      return
    }
    if (allMatchesPromise && allMatchesPromiseKey === cacheKey) {
      return allMatchesPromise
    }

    clearMemoryCache()
    allMatchesLoading.value = true
    allMatchesPromiseKey = cacheKey
    allMatchesPromise = loadAllMatches(maxId, matchesVersion)
      .finally(() => {
        allMatchesLoading.value = false
        allMatchesPromise = null
        allMatchesPromiseKey = ''
      })

    return allMatchesPromise
  }

  async function loadAllMatches(maxId: number, matchesVersion: string) {
    if (matchesVersion) {
      const cachedChunks = await loadCachedMatchChunks(matchesVersion, maxId)
      if (cachedChunks) {
        useMatchChunks(cachedChunks, maxId, matchesVersion)
        return
      }
    }

    const parsedChunks = await Promise.all(
      new Array(maxId + 1)
        .fill(0)
        .map((_, id) => fetchParsedMatches(id)),
    )
    useMatchChunks(parsedChunks, maxId, matchesVersion)

    if (matchesVersion) {
      void saveCachedMatchChunks(matchesVersion, maxId, parsedChunks)
        .catch((error) => console.warn('Could not persist match cache', error))
    }
  }

  function useMatchChunks(matchChunks: MatchFixture[][], maxId: number, matchesVersion: string) {
    const flatMatches = flattenChronologically(matchChunks)
    const byTeam = new Map<string, MatchFixture[]>()

    for (const match of flatMatches) {
      appendTeamMatch(byTeam, match.home, match)
      appendTeamMatch(byTeam, match.away, match)
    }

    allMatches.value = markRaw(flatMatches)
    matchesByTeam.value = markRaw(byTeam)
    earliestMatchDate.value = flatMatches[0]?.date ?? ''
    latestMatchDate.value = flatMatches[flatMatches.length - 1]?.date ?? ''
    loadedMatchesVersion.value = matchesVersion
    loadedMaxId = maxId
    allMatchesLoaded.value = true
  }

  function matchesInRange(fromDate: string, toDate: string): MatchFixture[] {
    return sliceByDate(allMatches.value, fromDate, toDate)
  }

  function matchesForTeam(teamName: string): MatchFixture[] {
    return matchesByTeam.value.get(teamName) ?? []
  }

  function teamMatchesInRange(teamName: string, fromDate: string, toDate: string): MatchFixture[] {
    return sliceByDate(matchesForTeam(teamName), fromDate, toDate)
  }

  function matchupMatches(teamNameA: string, teamNameB: string): MatchFixture[] {
    const teamMatches = matchesForTeam(teamNameA)
    return teamMatches.filter((match) =>
      (match.home === teamNameA && match.away === teamNameB) ||
      (match.home === teamNameB && match.away === teamNameA),
    )
  }

  function clearCache() {
    clearMemoryCache()
  }

  function clearMemoryCache() {
    matches.value = markRaw(new Map())
    allMatches.value = markRaw([])
    matchesByTeam.value = markRaw(new Map())
    parsedMatchesByYear.clear()
    allMatchesLoaded.value = false
    allMatchesLoading.value = false
    earliestMatchDate.value = ''
    latestMatchDate.value = ''
    loadedMatchesVersion.value = ''
    loadedMaxId = null
  }

  return {
    matches,
    allMatches,
    allMatchesLoaded,
    allMatchesLoading,
    earliestMatchDate,
    latestMatchDate,
    fetchMatches,
    ensureAllMatchesLoaded,
    matchesInRange,
    matchesForTeam,
    teamMatchesInRange,
    matchupMatches,
    clearCache,
  }
})

function appendTeamMatch(byTeam: Map<string, MatchFixture[]>, teamName: string, match: MatchFixture) {
  const teamMatches = byTeam.get(teamName)
  if (teamMatches) {
    teamMatches.push(match)
  } else {
    byTeam.set(teamName, [match])
  }
}

function flattenChronologically(matchChunks: MatchFixture[][]): MatchFixture[] {
  const flatMatches: MatchFixture[] = []
  for (let id = matchChunks.length - 1; id >= 0; id--) {
    flatMatches.push(...(matchChunks[id] ?? []))
  }
  return flatMatches
}

function sliceByDate(matches: MatchFixture[], fromDate: string, toDate: string): MatchFixture[] {
  if (matches.length === 0) {
    return []
  }
  const from = fromDate || '0000-01-01'
  const to = toDate || '9999-12-31'
  const start = lowerBoundByDate(matches, from)
  const end = upperBoundByDate(matches, to)
  return matches.slice(start, end)
}

function lowerBoundByDate(matches: MatchFixture[], date: string): number {
  let low = 0
  let high = matches.length
  while (low < high) {
    const mid = Math.floor((low + high) / 2)
    if (matches[mid]!.date < date) {
      low = mid + 1
    } else {
      high = mid
    }
  }
  return low
}

function upperBoundByDate(matches: MatchFixture[], date: string): number {
  let low = 0
  let high = matches.length
  while (low < high) {
    const mid = Math.floor((low + high) / 2)
    if (matches[mid]!.date <= date) {
      low = mid + 1
    } else {
      high = mid
    }
  }
  return low
}

async function loadCachedMatchChunks(matchesVersion: string, maxId: number): Promise<MatchFixture[][] | null> {
  const db = await openMatchCache()
  if (!db) {
    return null
  }

  try {
    const metadata = await idbGet<MatchCacheMetadata>(db, METADATA_STORE, ACTIVE_METADATA_KEY)
    if (
      !metadata ||
      metadata.version !== matchesVersion ||
      metadata.maxId !== maxId ||
      metadata.chunkIds.length !== maxId + 1
    ) {
      return null
    }

    const chunks: MatchFixture[][] = []
    for (const id of metadata.chunkIds) {
      const chunk = await idbGet<MatchCacheChunk>(db, MATCH_CHUNKS_STORE, id)
      if (!chunk || chunk.version !== matchesVersion) {
        return null
      }
      chunks.push(chunk.matches)
    }
    return chunks
  } catch (error) {
    console.warn('Could not load match cache', error)
    return null
  } finally {
    db.close()
  }
}

async function saveCachedMatchChunks(matchesVersion: string, maxId: number, matchChunks: MatchFixture[][]): Promise<void> {
  const db = await openMatchCache()
  if (!db) {
    return
  }

  try {
    const summary = summarizeChunks(matchChunks)
    await idbTransaction(db, [METADATA_STORE, MATCH_CHUNKS_STORE], 'readwrite', (transaction) => {
      const metadataStore = transaction.objectStore(METADATA_STORE)
      const chunksStore = transaction.objectStore(MATCH_CHUNKS_STORE)
      metadataStore.clear()
      chunksStore.clear()
      metadataStore.put({
        key: ACTIVE_METADATA_KEY,
        version: matchesVersion,
        maxId,
        chunkIds: matchChunks.map((_, id) => id),
        matchCount: summary.matchCount,
        earliestMatchDate: summary.earliestMatchDate,
        latestMatchDate: summary.latestMatchDate,
        createdAt: new Date().toISOString(),
      } satisfies MatchCacheMetadata)
      matchChunks.forEach((chunkMatches, id) => {
        chunksStore.put({
          id,
          version: matchesVersion,
          matches: chunkMatches,
        } satisfies MatchCacheChunk)
      })
    })
  } finally {
    db.close()
  }
}

function summarizeChunks(matchChunks: MatchFixture[][]) {
  let matchCount = 0
  let earliestMatchDate = ''
  let latestMatchDate = ''
  for (const chunk of matchChunks) {
    for (const match of chunk) {
      matchCount++
      if (!earliestMatchDate || match.date < earliestMatchDate) {
        earliestMatchDate = match.date
      }
      if (!latestMatchDate || match.date > latestMatchDate) {
        latestMatchDate = match.date
      }
    }
  }
  return { matchCount, earliestMatchDate, latestMatchDate }
}

function openMatchCache(): Promise<IDBDatabase | null> {
  if (!globalThis.indexedDB) {
    return Promise.resolve(null)
  }

  return new Promise((resolve, reject) => {
    const request = globalThis.indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(METADATA_STORE)) {
        db.createObjectStore(METADATA_STORE, { keyPath: 'key' })
      }
      if (!db.objectStoreNames.contains(MATCH_CHUNKS_STORE)) {
        db.createObjectStore(MATCH_CHUNKS_STORE, { keyPath: 'id' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

function idbGet<T>(db: IDBDatabase, storeName: string, key: IDBValidKey): Promise<T | undefined> {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(storeName, 'readonly')
    const request = transaction.objectStore(storeName).get(key)
    request.onsuccess = () => resolve(request.result as T | undefined)
    request.onerror = () => reject(request.error)
    transaction.onerror = () => reject(transaction.error)
  })
}

function idbTransaction(
  db: IDBDatabase,
  storeNames: string[],
  mode: IDBTransactionMode,
  callback: (transaction: IDBTransaction) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const transaction = db.transaction(storeNames, mode)
    transaction.oncomplete = () => resolve()
    transaction.onerror = () => reject(transaction.error)
    transaction.onabort = () => reject(transaction.error)
    callback(transaction)
  })
}

function parseMatchCsv(data: string): Promise<MatchFixture[]> {
  return new Promise((resolve) => {
    Papa.parse(data, {
      dynamicTyping: true,
      delimiter: ',',
      skipEmptyLines: true,
      complete: (results) => {
        const rows = results.data as MatchCsvRow[]
        resolve(rows.map(([
          date,
          home,
          away,
          homeScore,
          awayScore,
          homeRating,
          awayRating,
          pHome,
          pDraw,
          pAway,
          guessHome,
          guessAway,
          homeDiff,
          awayDiff,
          homeRd,
          awayRd,
        ]) => ({
          date,
          home,
          away,
          homeScore,
          awayScore,
          homeRating,
          awayRating,
          pHome,
          pDraw,
          pAway,
          guessHome,
          guessAway,
          homeDiff,
          awayDiff,
          homeRd,
          awayRd,
        })))
      },
    })
  })
}
