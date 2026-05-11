<script setup lang="ts">
import { ref } from 'vue'
import useMatchesStore from '@/stores/matches'
import Papa from 'papaparse'
import { fetchMetaData, metaData } from '@/simulator/SeasonSimulator'

type ExportMatchRow = {
  date: string
  home: string
  away: string
  homeScore: number
  awayScore: number
  winMargin: number
  homeRating: number
  awayRating: number
  ratingDifferenceAbs: number
  pHome: number
  pDraw: number
  pAway: number
  guessHome: number
  guessAway: number
  homeRatingChange: number
  awayRatingChange: number
  homeRd: number
  awayRd: number
}

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

const matchesStore = useMatchesStore()
const downloading = ref(false)
const hasDownloaded = ref(false)
const statusMessage = ref('')

function parseMatchCsv(data: string): Promise<ExportMatchRow[]> {
  return new Promise((resolve) => {
    Papa.parse(data, {
      dynamicTyping: true,
      delimiter: ',',
      skipEmptyLines: true,
      complete: (results) => {
        const rows = (results.data as MatchCsvRow[]).map(
          ([
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
            awayRd
          ]) => {
            const homeScoreNumber = Number(homeScore)
            const awayScoreNumber = Number(awayScore)
            const homeRatingNumber = Number(homeRating)
            const awayRatingNumber = Number(awayRating)
            return {
              date,
              home,
              away,
              homeScore: homeScoreNumber,
              awayScore: awayScoreNumber,
              winMargin: Math.abs(homeScoreNumber - awayScoreNumber),
              homeRating: homeRatingNumber,
              awayRating: awayRatingNumber,
              ratingDifferenceAbs: Math.abs(homeRatingNumber - awayRatingNumber),
              pHome,
              pDraw,
              pAway,
              guessHome,
              guessAway,
              homeRatingChange: homeDiff,
              awayRatingChange: awayDiff,
              homeRd,
              awayRd,
            }
          }
        )
        resolve(rows)
      },
    })
  })
}

function triggerDownload(csv: string, fileName: string) {
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

async function downloadAllMatchesAsCsv() {
  if (downloading.value) return
  downloading.value = true

  try {
    statusMessage.value = 'Metadata laden...'
    await fetchMetaData()

    statusMessage.value = 'Alle wedstrijdbestanden ophalen...'
    await Promise.all(
      new Array(metaData.MAX_ID + 1)
        .fill(0)
        .map((_, id) => matchesStore.fetchMatches(id))
    )

    statusMessage.value = 'Alle wedstrijden verwerken...'
    const allMatches: ExportMatchRow[] = []
    for (const data of matchesStore.matches.values()) {
      const parsedRows = await parseMatchCsv(data)
      allMatches.push(...parsedRows)
    }

    allMatches.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())

    statusMessage.value = 'CSV opbouwen en downloaden...'
    const csv = Papa.unparse(allMatches)
    triggerDownload(csv, 'alle-wedstrijden.csv')
    hasDownloaded.value = true
    statusMessage.value = `${allMatches.length} wedstrijden gedownload.`
  } catch (error) {
    console.error(error)
    statusMessage.value = 'Er ging iets mis tijdens het exporteren.'
  } finally {
    downloading.value = false
  }
}
</script>

<template>
  <div class="data-page">
    <h1>Data</h1>
    <p class="warning">
      Waarschuwing: deze download bevat alle wedstrijden uit de database en kan erg traag zijn en veel geheugen/CPU gebruiken.
    </p>
    <button @click="downloadAllMatchesAsCsv" :disabled="downloading || hasDownloaded">
      {{
        downloading
          ? 'Bezig met exporteren...'
          : hasDownloaded
            ? 'Al gedownload in deze sessie'
            : 'Download alle wedstrijden als CSV'
      }}
    </button>
    <p v-if="statusMessage">{{ statusMessage }}</p>

    <h2>Kolommen in de CSV</h2>
    <table>
      <thead>
      <tr>
        <th>Kolom</th>
        <th>Betekenis</th>
      </tr>
      </thead>
      <tbody>
      <tr><td>date</td><td>Wedstrijddatum (YYYY-MM-DD).</td></tr>
      <tr><td>home</td><td>Naam van het thuisspelende team.</td></tr>
      <tr><td>away</td><td>Naam van het uitspelende team.</td></tr>
      <tr><td>homeScore</td><td>Aantal gescoorde doelpunten door het thuisspelende team.</td></tr>
      <tr><td>awayScore</td><td>Aantal gescoorde doelpunten door het uitspelende team.</td></tr>
      <tr><td>winMargin</td><td>Doelpuntsverschil.</td></tr>
      <tr><td>homeRating</td><td>Rating van het thuisspelende team op wedstrijddatum.</td></tr>
      <tr><td>awayRating</td><td>Rating van het uitspelende team op wedstrijddatum.</td></tr>
      <tr><td>ratingDifferenceAbs</td><td>Absoluut ratingverschil tussen beide teams: <code>|homeRating - awayRating|</code>.</td></tr>
      <tr><td>pHome</td><td>Modelkans op winst van het thuisspelende team.</td></tr>
      <tr><td>pDraw</td><td>Modelkans op een gelijkspel.</td></tr>
      <tr><td>pAway</td><td>Modelkans op winst van het uitspelende team.</td></tr>
      <tr><td>guessHome</td><td>Verwachte score van het thuisspelende team.</td></tr>
      <tr><td>guessAway</td><td>Verwachte score van het uitspelende team.</td></tr>
      <tr><td>homeRatingChange</td><td>Ratingverandering van het thuisspelende team door de wedstrijd.</td></tr>
      <tr><td>awayRatingChange</td><td>Ratingverandering van het uitspelende team door de wedstrijd.</td></tr>
      <tr><td>homeRd</td><td>RD (rating-onzekerheid) van het thuisspelende team.</td></tr>
      <tr><td>awayRd</td><td>RD (rating-onzekerheid) van het uitspelende team.</td></tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.data-page {
  max-width: 900px;
}

.warning {
  font-weight: 600;
}

button {
  margin-bottom: 1rem;
}

table {
  border-collapse: collapse;
  width: 100%;
}

th, td {
  border: 1px solid var(--color-border);
  padding: 0.5rem 0.75rem;
  text-align: left;
}

th {
  background-color: var(--color-background-mute);
}
</style>
