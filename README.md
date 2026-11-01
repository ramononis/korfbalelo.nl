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

- `SeasonPredicter.doOutdoor` is hardcoded (`false`) unless changed in code.
- Test coverage is limited; `GlickoTest` is disabled.
- Large generated data files can change often; avoid unrelated formatting churn.
- `web/dist` and `web/node_modules` are build outputs and should not be committed.

## Verified Commands (current workspace)

These passed during doc creation:

- `./gradlew clean test`
- `cd web && npm ci && npm run build`

## Glicko investigation — 2026-11-01 (AI generated)

The default model now uses **margin scale 360** (previously 400) and a **score-learning
weight cap of 4** (previously unlimited). The remaining defaults are unchanged.
Full configurations, development results, later-period results, date clusters,
season summaries, and bootstrap comparisons are in
[`benchmarks/glicko-2026-11-01/`](benchmarks/glicko-2026-11-01/).

### What the current calculation does

This is a sequential, margin-aware adaptation of Glicko-2, rather than textbook
win/draw/loss Glicko-2:

1. Before a match, `Team.setNewRD` increases squared normalized RD by elapsed days
   divided by `rdPeriodDays`, multiplied by volatility squared. RD is capped at 350.
2. A normal goal-difference distribution uses each team's learned scoring pace:
   team score SD = `0.166 * averageScore + 1.85`. Monthly offsets adjust scoring pace.
   Its mean is `differenceSD * inverseNormalCDF(p)`, where
   `p = 1 / (1 + 10^((awayRating - homeRating - H) / marginScale))`.
3. The match result becomes a fractional Glicko score:
   `normalCDF(actualGoalDifference / differenceSD)`. A narrow win is weaker evidence
   than a large win; the predicted mean cancels out of this transformation.
4. `Glicko2Calculator` computes opponent-RD attenuation, expected score, information,
   volatility, posterior RD and rating. Both teams use their opponents' pre-update
   ratings. Home advantage learns after the away-side update has been calculated.
5. Scoring pace learns from the team's goal residual. A daily cross-team regression
   also supplies a small goal-based rating adjustment. Ratings are recentered daily.

The volatility root solver agrees structurally with
[Glickman's reference](https://www.glicko.net/glicko/glicko2.pdf).
The calculator omits the reference's additional per-update volatility inflation;
this application already ages RD by elapsed time before updating. Adding another
full period per match would change the time model and can double-count aging.
The one-game fractional-score updates and calendar-day periods are deliberate
adaptations that need empirical validation; textbook Glicko-2 recommends batches.
No change to the volatility solver or an extra per-match inflation was made.

### Why bounded scoring-pace learning helps

Previously, the goal residual was multiplied by `v * g = 1 / (g * E * (1-E))`
and divided by 100. This multiplier grows rapidly in uneven matchups, letting those
games dominate scoring-pace learning. It is always at least 4. Consequently,
a cap of 4 makes the learning rate a stable **4% of the goal residual per match**.
The Glicko rating and RD equations themselves remain unchanged; subsequent ratings
can change through the learned scoring pace and the existing goal-based adjustment.
Caps 4, 8, 16 and 32 were tested; 4 gave the best development team-score error.

A smaller margin scale sharpens forecast probabilities. Scale 360 with the cap
provided a useful balance between probability quality and predicted goal errors.
The browser playoff fallback and backend declarative playoff fallback now use the
configured scale too; the browser retains 400 for older metadata without a scale.
New metadata records the score-learning cap.

### Method and results

First ran 27 ablations, then 19 interaction variants on **96,623 matches**, evaluating
2000-08-01 through 2022-08-01. Earlier events trained the model. Four finalists were
frozen before inspecting the later period: original baseline, cap only, balanced
360 + cap, and probability-focused 320 + SD coefficients 0.12/1.5 + cap.

Later-period evaluation requested 2022-08-01 through 2026-11-01; the available played
matches actually span **2022-09-10 through 2026-10-17**, totaling **16,674 matches**.
Every prediction is recorded before that match updates the model. All variants
use the same events and daily recentering. Lower is better for the four error metrics.

| Later-period metric | Original | Cap only | New default (360 + cap) | Probability-focused candidate |
| --- | ---: | ---: | ---: | ---: |
| Three-outcome Brier | 0.409093 | 0.408170 | **0.404770** | 0.401882 |
| Log loss | 0.709068 | 0.707565 | **0.701685** | 0.693145 |
| Goal-margin MAE | 4.182477 | 4.178350 | **4.154313** | 4.180452 |
| Per-team score MAE | 3.390786 | 3.295007 | **3.281762** | 3.293694 |
| Win/draw/loss accuracy | 70.70289% | 70.66691% | **70.65491%** | 70.57695% |

The new default improves Brier **1.06%**, log loss **1.04%**, margin MAE **0.67%**,
and team-score MAE **3.22%** relative to the original. WDL classification drops
0.048 percentage points (eight predictions); this is an improvement in probability
and score quality, with essentially unchanged classification accuracy.
The probability-focused candidate remains available in `finalists`, but was not
chosen as the default because it provides less improvement in margin prediction.

The balanced model improves all four error metrics in each season slice:
2022–23, 2023–24, 2024–25, 2025–26 and partial 2026–27. Its development Brier,
margin MAE and team-score MAE were 0.435306, 3.724715 and 3.019077, compared with
0.439099, 3.726555 and 3.055818 for the original.

Paired bootstrap resampling of **42 calendar-month clusters**, 4,000 samples,
seed 20261101, gives these approximate 95% intervals for new minus original:

| Metric | Difference | Interval |
| --- | ---: | ---: |
| Brier | -0.004323 | [-0.004918, -0.003729] |
| Log loss | -0.007383 | [-0.008549, -0.006157] |
| Margin MAE | -0.028163 | [-0.038418, -0.018521] |
| Team-score MAE | -0.109024 | [-0.125409, -0.093180] |

### Benchmark fixes and limits

The old benchmark set `ignoreMatchesBelow` to the evaluation start, discarding
rating updates from earlier history. Both benchmark entrypoints now retain earlier
training matches and exclude events after the evaluation end. A regression test
checks that moving the evaluation start changes the measured matches without
changing the final trained rating, and that future games are excluded.

`startratings.csv` is fitted using later results by `StartRatingFinder`, so replay
with these priors is not strictly out of sample. Repeated all four finalists with
those priors removed: original versus balanced Brier was **0.409206 → 0.404871**,
log loss **0.709231 → 0.701838**, margin MAE **4.183550 → 4.154784**, and team-score
MAE **3.390658 → 3.282039**. The same improvements survive this sensitivity check.
The development search still used the fitted priors; this is historical validation,
not an untouched prospective experiment. Later-period selection between finalists
also means the final choice should now be monitored on future matches.

Month resampling preserves dependence within a month, but not dependence across
months or repeated clubs throughout a season. The intervals are descriptive,
not proof of generalization. Matches have dates rather than kickoff timestamps;
same-date results are processed in file order. Results mix indoor and outdoor games
and do not yet establish separate calibration for each competition or tier.

Slower RD growth did not help: periods of 7 and 14 days raised development Brier
to 0.456359 and 0.465694. Removing seasonality worsened team-score MAE from 3.055818
to 3.227325. Removing the score-based rating tweak slightly worsened Brier.
Home-advantage learning changes gave negligible gains. These mechanisms were kept.

### Further worthwhile experiments

- Evaluate probability calibration by rating gap, RD, competition, and season type.
  Direct match forecasts use point ratings; season simulations additionally sample
  rating uncertainty. Test integrating uncertainty into direct forecasts, separately
  from changing the update equations.
- Fit score variance and draw probabilities independently from rating updates.
  Currently the same SD controls fractional learning targets, margin predictions,
  and draw probabilities, so optimizing one objective can worsen another.
- Separate attack/scoring pace from defensive strength, and consider robust or
  count-based goal likelihoods. The current pace update mixes own and opponent pace;
  the scalar goal-based rating tweak can reward high-scoring styles.
- Replace outcome-fitted spawn priors with causal priors based only on information
  available when a club enters. Evaluate newcomers separately.
- Validate day-batched updates and season-boundary uncertainty against sequential
  calendar aging, with a future period reserved before additional tuning.

### Reproduction and verification

Use Java 25. The CSV rows include full model configurations. Finalist order is
0 = original, 1 = cap only, 2 = new default, 3 = probability-focused candidate.
`research` and `refine` intentionally retain the original reference defaults.
Scoring-tweak and seasonality settings come from system properties; the checked-in
results use their normal production settings.

```bash
# AI generated: discovery, interaction search, then frozen finalist evaluation.
./gradlew benchmarkModel --args=research -Delo.benchmark.from=2000-08-01 -Delo.benchmark.to=2022-08-01 -Delo.benchmark.output=build/development.csv
./gradlew benchmarkModel --args=refine -Delo.benchmark.from=2000-08-01 -Delo.benchmark.to=2022-08-01 -Delo.benchmark.output=build/refinement.csv
./gradlew benchmarkModel --args=finalists -Delo.benchmark.from=2022-08-01 -Delo.benchmark.to=2026-11-01 -Delo.benchmark.byDate=true -Delo.benchmark.output=build/holdout.csv
./gradlew benchmarkModel --args=finalists -Delo.benchmark.from=2022-08-01 -Delo.benchmark.to=2026-11-01 -Delo.benchmark.fittedStarts=false -Delo.benchmark.byDate=true -Delo.benchmark.output=build/holdout-unfitted.csv
python3 benchmarks/analyze_glicko.py build
```

Restore the original runtime behavior with
`-Delo.model.marginRatingScale=400 -Delo.model.scoreLearningWeightCap=Infinity`.

Verification: `./gradlew clean test` completed with 62 passing tests and two skipped;
frontend `npm run build` and all 15 frontend tests passed. Rating output and a
current-season prediction export with two deterministic iterations completed in a
disposable repository copy. Exported metadata showed scale 360 and cap 4, and
34 indoor poule CSVs contained the 2026-11-01 snapshot. These iterations only verify
the export path, not Monte Carlo convergence. The prescribed January 2026 snapshot
failed because current poules reference WKS before it exists in that historical
ranking. No production ranking, graph, match, metadata or season CSV artifacts in
this checkout were regenerated; only the benchmark CSVs are new generated outputs.
