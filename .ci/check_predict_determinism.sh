#!/usr/bin/env bash
set -euo pipefail

SNAP1=$(mktemp -d)
SNAP2=$(mktemp -d)
cleanup() {
  rm -rf "$SNAP1" "$SNAP2"
}
trap cleanup EXIT

# Deterministic mode: fixed seed, no concurrency race, reduced iterations for CI speed.
COMMON_PROPS=(
  -Delo.predict.deterministic=true
  -Delo.predict.concurrent=1
  -Delo.predict.iterations=10000
  -Delo.predict.seed=20260101
)

# Use the most recent match date to avoid replaying a large historical date window.
PREDICT_DATE=${ELO_PREDICT_DATE:-$(awk -F, '/^[0-9]{4}-[0-9]{2}-[0-9]{2}/ {print $1}' matches/*.txt matches/*.csv | sort | tail -n 1)}
if [[ -z "$PREDICT_DATE" ]]; then
  echo "Unable to determine predict date from matches/, set ELO_PREDICT_DATE explicitly." >&2
  exit 2
fi

echo "Determinism check using start date: $PREDICT_DATE"

./gradlew --no-daemon predict --args="$PREDICT_DATE" "${COMMON_PROPS[@]}"
cp -R web/public/csv "$SNAP1/csv"

./gradlew --no-daemon predict --args="$PREDICT_DATE" "${COMMON_PROPS[@]}"
cp -R web/public/csv "$SNAP2/csv"

diff -ru "$SNAP1/csv" "$SNAP2/csv"
echo "Determinism check passed: repeated predict outputs are identical."
