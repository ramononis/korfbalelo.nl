# [korfbalelo.nl](https://korfbalelo.nl)

First, a disclaimer:
 - This is a hobby project that started somewhere in 2018 and has escalated quite a bit over the years
 - The code is exactly like that of a hobby project: chaotic, illogical, incorrect, sparsely documented, and appearing to be written by an insane lunatic (me)
 - From 2026 onwards, most changes are AI generated and possibly even more incoherent and overly complicated than I could've ever done
 - PRs are welcome and will be looked at. They will also be frowned upon, because who in their sane mind would go through the effort trying to understand this mess?
 - Complaints about the state of the codebase will be met with maniacal laughter.

For a nice explanation of what this all about anyway, see [korfbalelo.nl](https://korfbalelo.nl).

The following is AI generated (because I'm lazy) and could be partly or totally wrong:

`Korfbal Elo` is a Kotlin + Vue project for rating Dutch korfbal teams, predicting match outcomes, and simulating league finish probabilities (champion/promotion/relegation).

It combines:
- A Kotlin rating/simulation engine (`src/main/kotlin`)
- Data ingestion (historical files + KNKV portal API scraping with cache)
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

Root and frontend both pin Node major version `26` via `.nvmrc`.

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
  - Uses validated raw-response cache files under `cache/knkv-v2-*.json`
  - Writes `matches/current.txt` and `web/public/specials<indoor-season>.json`

### 2) Rating engine

- Entrypoint: `src/main/kotlin/nl/korfbalelo/elo/ApplicationNew.kt`
- Team state + per-match update: `src/main/kotlin/nl/korfbalelo/elo/Team.kt`
- Glicko rating equations: `src/main/kotlin/nl/korfbalelo/elo/GlickoCalculator.kt`
- Shared calibration: `src/main/kotlin/nl/korfbalelo/elo/RatingModelConfig.kt`
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
- `src/main/kotlin/nl/korfbalelo/mijnkorfbal`: KNKV portal API models + scraper
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
  - Runs `./gradlew -Delo.scraper.forceNetwork=true scrape`
  - If `matches/current.txt` changed (including removed/corrected results), trims outdoor season CSV history from the earliest changed date and runs `./gradlew predict --args="$EARLIEST_DATE veld"`
  - Commits and pushes updated data artifacts
  - Forces fresh KNKV portal API reads (`-Delo.scraper.forceNetwork=true`) and saves cache snapshot key `knkv-cache-v2-<os>-daily-*`
  - Other workflows restore the latest successful daily scrape snapshot (they do not overwrite it)

<!-- AI generated: KNKV portal migration and operational controls. -->
## KNKV Scraper

The official portal API base is `https://api-saas-site-prod-236.dotlab.net/general/api/competition`.
The scraper validates the success envelope and endpoint payload before caching it. Results are
flat matches; programme responses contain week groups. Match dates use the API's local `date`,
and unplayed fixtures retain scores of `-1`. Requests are paced at five per second with bounded
retries for rate limits and temporary gateway failures. Failed scrapes leave match output and
published in-memory competition data untouched.

The legacy `KNKV-DISTRICT-OOST` filter now labels nationwide **B-category** competitions.
The outdoor SN and RN pool names/IDs match the previous cached source (67 and 156 pools,
respectively, checked October 11, 2026). Both teams must still end in ` 1` to enter Elo input.
The versioned `static-poules/zaal2627.json` remains the default indoor source. Upcoming live
pools with no standings, results, or programme are skipped as unpublished; published matches
without standings still cause a clear failure.

A fresh live check can write both match and specials output outside the checkout:

```bash
./gradlew -Delo.scraper.forceNetwork=true -Delo.scraper.outputDirectory=/tmp/knkv-check scrape
```

Other scraper properties:

- `elo.scraper.apiBase`: override the API base for a future endpoint move.
- `elo.scraper.cacheDirectory`: override the cache directory (default `cache`).
- `elo.scraper.useStaticIndoorPoules=false`: explicitly test all live indoor sources.
- `elo.scraper.writeCurrentMatches=false`: suppress match-file writes.
- `elo.scraper.writeSpecialMatches=false`: suppress specials-file writes as well.
- `elo.scraper.requestIntervalMillis`: minimum interval between requests (default `200`).

## License

Source code is licensed under the MIT License; see [LICENSE](LICENSE).

Match data, fixture data, ranking data, generated CSV/JSON artifacts, and other
data/content files are published for transparency and reproducibility. They may
include data derived from third-party or public sources. Reuse of those
data/content files is not covered by the MIT source-code license.

## Known Caveats

- `predict` accepts a date and optional `zaal` or `veld` argument; the default is `zaal`.
- Test coverage is limited; rating equations, calendar aging and simulation rules have focused tests.
- Large generated data files can change often; avoid unrelated formatting churn.
- `web/dist` and `web/node_modules` are build outputs and should not be committed.

## Verified Commands (current workspace)

These passed during doc creation:

- `./gradlew clean test`
- `cd web && npm ci && npm run build`

## Rating model

<!-- AI generated -->
Korfbal Elo uses Glicko, updated after each match. Every team has a rating,
a rating deviation (RD, uncertainty in rating points), and an estimated scoring
pace. Both teams are updated from their pre-match state. Home advantage is
estimated across all teams and changes gradually with match results.

Uncertainty grows with elapsed calendar time using a shared rate:

```text
RD_before_match = min(450, sqrt(RD_previous² + elapsed_days * 10.423068²))
```

A new team starts with RD 337.5. A match reduces RD according to the information
provided by the opponent. The growth rate is the same for every team; it is a
model constant. Splitting an idle interval into several steps adds the same
variance as aging it in one step. Browser simulations use the exported growth
rate and RD cap when sampling future team strength.

The observed goal margin is converted into a fractional result:
`S = Φ(goal_margin / margin_sd)`. A draw gives 0.5; larger wins approach 1 and
larger losses approach 0. For a team with expected scoring pace `μ`, its score
standard deviation is `0.12 * μ + 1.9`. The margin SD is the square root of the
sum of the two teams' score variances. Forecasts convert the home-adjusted rating
difference into an expected margin using rating scale 320. The Glicko update
uses its standard logistic rating scale, expressed internally in normalized
units of 173.7178 rating points.

Scoring pace learns from each team's score residual, with inverse learning speed
100 and an information weight capped at 4. A small rating correction also learns
from the relationship between rating and scoring pace (rate 0.001, maximum 10
points per match, after four matches). Monthly score offsets track seasonal
scoring differences (learning rate 0.005, cap 6 goals, half-life 1825 days).
The initial home advantage is 35 points and its learning rate is 1/64.
Ratings are recentered across active teams during replay.

`RatingModelConfig`, `ScoreRatingTweakConfig` and `ScoreSeasonalityConfig` define
the defaults. `elo.model.*` system properties override individual settings,
for example `-Delo.model.rdGrowthPerSqrtDay=10.423068`.

`ranking.csv` contains, in order: name, rating, last rating date, first match
date, season rating change, place, RD and scoring pace. `meta.json` exports
`RD_GROWTH_PER_SQRT_DAY`, `RD_MAX`, the forecast constants and monthly score offsets
for the frontend.
