# korfbalelo.nl

First, a disclaimer:
 - This is a hobby project that started somewhere in 2018 and has escalated quite a bit over the years
 - The code is exactly like that of a hobby project: chaotic, illogical, incorrect, sparsely documented, and appearing to be written by an insane lunatic (me)
 - From 2026 onwards, most changes are AI generated and possibly even more incoherent and overly complicated than I could've ever done
 - PRs are welcome and will be looked at. They will also be frowned upon, because who in their sane mind would go through the effort trying to understand this mess?
 - Complaints about the state of the codebase will be met with maniacal laughter.

The following is AI generated (because I'm lazy) and could be partly or totally wrong:

`Korfbal Elo` is a Kotlin + Vue project for rating Dutch korfbal teams, predicting match outcomes, and simulating league finish probabilities (champion/promotion/relegation).

It combines:
- A Kotlin rating/simulation engine (`src/main/kotlin`)
- Data ingestion (historical files + Sportlink API scraping with cache)
- A Vue 3 frontend (`web/`) that serves and visualizes generated artifacts

## Stack

- Kotlin JVM (Gradle, Java toolchain 25)
- Vue 3 + TypeScript + Vite + Pinia
- Data files in CSV/TXT/JSON (many are generated and committed)

## Prerequisites

### Java 25 via sdkman

This repo is configured for Java 25 in `build.gradle.kts` and CI.

```bash
sdk list java | grep -E "25.*(tem|open|zulu)"
sdk install java <candidate>
sdk use java <candidate>
java -version
```

### Node.js

Root and frontend both pin Node major version `20` via `.nvmrc`.

```bash
nvm use
node -v
```

`npm >= 10` is recommended. `npm 8` works but prints engine warnings for `npm-run-all2`.

## Quick Start

### Backend

```bash
./gradlew clean test
./gradlew run
```

- `run` executes `nl.korfbalelo.elo.ApplicationNew`
- It recalculates ratings and writes web-consumed artifacts like:
  - `web/public/ranking.csv`
  - `web/public/graph.csv`
  - `web/public/meta.json`
  - `web/src/origins.ts`
  - `aggr/*.csv`

### Season prediction export

```bash
./gradlew predict --args="2026-01-01"
```

- Runs `nl.korfbalelo.elo.SeasonPredicter`
- Generates season probability CSVs/JSON under `web/public/csv/...` and `web/public/*.json`
- Note: CI currently passes a second argument (`zaal`) but `SeasonPredicter.main` only uses the first argument (date)

### Frontend

```bash
cd web
npm ci
npm run dev
```

Useful checks:

```bash
cd web
npm run build
npm run lint
```


## Quality Gates

Baseline gates (fast PR feedback):

- `./gradlew test`
- `cd web && npm run build`

For output-producing backend changes also run:

- `./gradlew run`
- `./gradlew predict --args="2026-01-01"`

See `docs/quality-gates.md` for details and invariants.
CI contract note: PR pipeline enforces earliest-date guard + predict determinism by default; add PR label `changes generated match output` to intentionally skip those contract checks.

## Architecture Overview

### 1) Event + match ingestion

- Historical data: `matches/*.txt|csv`
- Club lifecycle commands (spawn/merge/alias/end): `club_events.txt`
- Current season scrape: `src/main/kotlin/nl/korfbalelo/mijnkorfbal/Scraper.kt`
  - Uses cache files under `cache/`
  - Writes `matches/current.txt` and `web/public/specialszaal2526.json`

### 2) Rating engine

- Entrypoint: `src/main/kotlin/nl/korfbalelo/elo/ApplicationNew.kt`
- Team model + Glicko-style update: `src/main/kotlin/nl/korfbalelo/elo/Team.kt`
- Match application + alias mapping: `src/main/kotlin/nl/korfbalelo/elo/RankingNew.kt`
- Event parsing/execution: `src/main/kotlin/nl/korfbalelo/elo/RankingEvent.kt`

### 3) Season simulation

- Kotlin simulator/exporter: `src/main/kotlin/nl/korfbalelo/elo/SeasonPredicter.kt`
- Poule simulator core: `src/main/kotlin/nl/korfbalelo/elo/PoulePredicter.kt`
- Outputs consumed by frontend:
  - `web/public/csv/<season>/<poule>.csv`
  - `web/public/<season>.json`
  - `web/public/meta.json`

### 4) Frontend

- Vue app shell/routes: `web/src/App.vue`, `web/src/router/index.ts`
- Data stores: `web/src/stores/*`
- Season + what-if simulator:
  - `web/src/simulator/SeasonSimulator.ts`
  - `web/src/simulator/PoulePredicter.ts`
  - `web/src/simulator/SimulationWorker.ts`

Important: promotion/relegation logic exists in both Kotlin (`SeasonPredicter.kt`) and frontend TS (`SeasonSimulator.ts`) and should stay aligned.

## Repo Layout

- `src/main/kotlin/nl/korfbalelo/elo`: rating engine, event model, simulation, exports
- `src/main/kotlin/nl/korfbalelo/mijnkorfbal`: Sportlink API models + scraper
- `src/test/kotlin`: minimal tests (some disabled)
- `matches/`: historical + current match data files
- `web/`: Vue frontend
- `web/public/`: generated and static data served by frontend
- `.github/workflows/`: CI and scheduled daily update workflow

## CI/CD

- `.github/workflows/pr.yml`
  - Java 25
  - `./gradlew build`
  - generated-output contracts:
    - `./.ci/check_generated_csv_guard.py <base-ref>`
    - `./.ci/check_predict_determinism.sh`
    - optional PR label override: `changes generated match output`
  - `web: npm ci && npm run build`
- `.github/workflows/daily-check.yml`
  - Runs `./gradlew run`
  - If `matches/current.txt` changed, trims outdoor season CSV history from the earliest changed date and runs `./gradlew predict --args="$EARLIEST_DATE veld"`
  - Commits and pushes updated data artifacts
  - Forces fresh Sportlink API reads (`-Delo.scraper.forceNetwork=true`) and saves cache snapshot key `sportlink-cache-v1-<os>-daily-*`
  - Other workflows restore the latest daily snapshot (they do not overwrite it)

## License

Source code is licensed under the MIT License; see [LICENSE](LICENSE).

Match data, fixture data, ranking data, generated CSV/JSON artifacts, and other
data/content files are published for transparency and reproducibility. They may
include data derived from third-party or public sources. Reuse of those
data/content files is not covered by the MIT source-code license.

## Known Caveats

- `SeasonPredicter.doOutdoor` is hardcoded (`false`) unless changed in code.
- Test coverage is limited; `GlickoTest` is disabled.
- Large generated data files can change often; avoid unrelated formatting churn.
- `web/dist` and `web/node_modules` are build outputs and should not be committed.

## Verified Commands (current workspace)

These passed during doc creation:

- `./gradlew clean test`
- `cd web && npm ci && npm run build`
