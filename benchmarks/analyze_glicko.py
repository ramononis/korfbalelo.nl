#!/usr/bin/env python3
# AI generated: paired calendar-month bootstrap and season summaries for the Glicko investigation.
import collections
import csv
from pathlib import Path
import random
import sys

METRICS = ('brier', 'logLoss', 'marginMae', 'teamScoreMae')


def analyze(directory):
    comparisons, seasons = [], []
    for scenario in ('holdout', 'holdout-unfitted'):
        rows = list(csv.DictReader((directory / f'{scenario}-by-date.csv').open()))
        by_month = collections.defaultdict(lambda: collections.defaultdict(list))
        by_season = collections.defaultdict(list)
        for row in rows:
            variant = int(row['variant'])
            by_month[variant][row['date'][:7]].append(row)
            year, month = map(int, row['date'].split('-')[:2])
            season = year if month >= 8 else year - 1
            by_season[variant, season].append(row)
        for (variant, season), group in sorted(by_season.items()):
            n = sum(int(row['matches']) for row in group)
            seasons.append(dict(scenario=scenario, variant=variant, season=f'{season}-{season+1}',
                                matches=n, **{metric: sum(float(row[metric]) * int(row['matches'])
                                    for row in group) / n for metric in METRICS}))
        for variant in sorted(by_month.keys() - {0}):
            assert by_month[variant].keys() == by_month[0].keys()
            for metric in METRICS:
                blocks = []
                for month, reference in by_month[0].items():
                    candidate = by_month[variant][month]
                    assert [(r['date'], r['matches']) for r in reference] == [
                        (r['date'], r['matches']) for r in candidate]
                    n = sum(int(r['matches']) for r in reference)
                    delta = sum(float(r[metric]) * int(r['matches']) for r in candidate) - sum(
                        float(r[metric]) * int(r['matches']) for r in reference)
                    blocks.append((delta, n))
                rng = random.Random(20261101)
                samples = []
                for _ in range(4000):
                    sample = rng.choices(blocks, k=len(blocks))
                    samples.append(sum(x[0] for x in sample) / sum(x[1] for x in sample))
                samples.sort()
                comparisons.append(dict(scenario=scenario, variant=variant, metric=metric,
                    delta=sum(x[0] for x in blocks) / sum(x[1] for x in blocks),
                    ci025=samples[100], ci975=samples[3899], months=len(blocks)))
    for name, result in [('comparisons', comparisons), ('seasons', seasons)]:
        with (directory / f'{name}.csv').open('w') as output:
            writer = csv.DictWriter(output, fieldnames=list(result[0]))
            writer.writeheader()
            writer.writerows(result)


if __name__ == '__main__':
    analyze(Path(sys.argv[1] if len(sys.argv) > 1 else 'benchmarks/glicko-2026-11-01'))
