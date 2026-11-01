#!/usr/bin/env python3
# AI generated: reproduce cohort statistics, paired month intervals and the volatility figure.
import collections
import csv
import json
from pathlib import Path
import random
import statistics
import sys

METRICS = ('brier', 'logLoss', 'marginMae', 'teamScoreMae')
RECENT = '2025-08-01'


def read(path):
    with path.open() as f:
        return list(csv.DictReader(f))


def write(path, rows):
    with path.open('w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def cohort(row):
    year = int(row['created'][:4])
    return 'Before 1946' if year < 1946 else '1946–1969' if year < 1970 else '1970 entry' if year == 1970 else 'After 1970'


def analyze(directory):
    teams = read(directory / 'holdout/learned-teams.csv')
    recent = [r for r in teams if r['lastMatch'] >= RECENT and r['origins'] == '1']
    groups = collections.defaultdict(list)
    for row in recent:
        groups[cohort(row)].append(row)
    cohorts = []
    for name, rows in groups.items():
        cohorts.append(dict(cohort=name, teams=len(rows),
            medianGames=statistics.median(float(r['games']) for r in rows),
            medianSigma=statistics.median(float(r['sigma']) for r in rows)))
    write(directory / 'cohorts.csv', cohorts)
    rho = statistics.correlation([float(r['games']) for r in recent], [float(r['sigma']) for r in recent])
    print(f'Recent single-lineage teams: {len(recent)}; correlation(games,sigma)={rho:.9f}')
    games = [float(r['games']) for r in recent]
    inverse = [1 / float(r['sigma']) ** 2 for r in recent]
    fit = statistics.linear_regression(games, inverse)
    active = [r for r in teams if r['lastMatch'] >= RECENT]
    facts = dict(ai_generated=True, active_since=RECENT, roster_teams=len(teams),
        all_below_starting_sigma=all(float(r['sigma']) < .06 for r in teams),
        recent_teams=len(active), recent_single_lineage_teams=len(recent),
        games_sigma_correlation_single_lineage=rho,
        games_sigma_correlation_all_recent=statistics.correlation(
            [float(r['games']) for r in active], [float(r['sigma']) for r in active]),
        inverse_sigma_squared_intercept=fit.intercept,
        inverse_sigma_squared_slope=fit.slope,
        inverse_sigma_squared_r2=statistics.correlation(games, inverse) ** 2)
    (directory / 'facts.json').write_text(json.dumps(facts, indent=2) + '\n')
    resets = {r['name']: r for r in read(directory / 'holdout/reset-1970-teams.csv')}
    before = [r for r in recent if r['created'] < '1970-08-30']
    changes = [float(resets[r['name']]['sigma']) - float(r['sigma']) for r in before]
    print(f'Reset-1970 pre-entry clubs: {len(before)}; positive={sum(d > 0 for d in changes)}; '
          f'median sigma change={statistics.median(changes):.9f}; max={max(changes):.9f}')
    comparisons = []
    for scenario in ['holdout', 'unfitted']:
        def months(rows):
            groups = collections.defaultdict(list)
            for r in rows:
                groups[r['date'][:7]].append(r)
            return groups
        base = months(read(directory / scenario / 'learned-by-date.csv'))
        for summary in read(directory / scenario / 'summary.csv'):
            variant = summary['variant']
            if variant == 'learned':
                continue
            alternative = months(read(directory / scenario / f'{variant}-by-date.csv'))
            assert alternative.keys() == base.keys()
            for metric in METRICS:
                blocks = []
                for month, rows in base.items():
                    other = alternative[month]
                    assert [(r['date'], r['matches']) for r in rows] == [(r['date'], r['matches']) for r in other]
                    n = sum(int(r['matches']) for r in rows)
                    delta = sum(float(r[metric]) * int(r['matches']) for r in other) - sum(
                        float(r[metric]) * int(r['matches']) for r in rows)
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
    write(directory / 'comparisons.csv', comparisons)
    plot(directory, recent=recent, resets=resets, rho=rho)


def plot(directory, recent, resets, rho):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.ticker import FormatStrFormatter
    fig, (ax, right) = plt.subplots(1, 2, figsize=(12, 5), gridspec_kw={'width_ratios': [1.5, 1]})
    colors = ['#2864a0', '#68a8bb', '#d78222', '#74934c']
    for name, color in zip(['Before 1946', '1946–1969', '1970 entry', 'After 1970'], colors):
        group = [r for r in recent if cohort(r) == name]
        ax.scatter([int(r['games']) for r in group], [float(r['sigma']) for r in group],
                   label=f'{name} (n={len(group)})', color=color, alpha=.8, s=23)
    ax.axhline(.06, color='#666666', linestyle='--', linewidth=1)
    ax.set(xlabel='Recorded matches', ylabel='Current volatility σ',
           title=f'Match count dominates volatility (r = {rho:.3f})')
    ax.legend(frameon=False, fontsize=9)
    ax.yaxis.set_major_formatter(FormatStrFormatter('%.3f'))
    by_name = {r['name']: r for r in recent}
    names = ['Blauw Wit', 'KZ', 'PKC', 'DVO', "Olympia '22"]
    for i, name in enumerate(names):
        learned = float(by_name[name]['sigma'])
        reset = float(resets[name]['sigma'])
        right.plot([learned, reset], [i, i], color='#aaaaaa', linewidth=2)
        right.scatter(learned, i, color='#2864a0', label='Normal replay' if i == 0 else None, s=45)
        right.scatter(reset, i, color='#d78222', marker='D',
                      label='Reset all σ in Aug 1970' if i == 0 else None, s=30)
    right.set_yticks(range(len(names)), names)
    right.invert_yaxis()
    right.set(xlabel='Current volatility σ', title='Pre-1970 history still affects today')
    right.legend(frameon=False, fontsize=8, loc='upper right')
    right.set_xlim(.0535, .06)
    right.xaxis.set_major_formatter(FormatStrFormatter('%.3f'))
    for panel in (ax, right):
        panel.spines[['top', 'right']].set_visible(False)
        panel.grid(alpha=.15)
    fig.suptitle('Korfbalelo volatility audit · data through 17 October 2026', fontsize=14, y=1.02)
    fig.text(.5, -.02, 'Left: active since Aug 2025, single-lineage clubs only. Entry year is dataset entry, not founding year or religion.\n'
             'Right: all later matches are replayed after the reset; small opponent-feedback effects are included. AI-generated analysis.',
             ha='center', fontsize=8, color='#555555')
    fig.tight_layout()
    fig.savefig(directory / 'volatility.png', dpi=180, bbox_inches='tight')
    plt.close(fig)


if __name__ == '__main__':
    analyze(Path(sys.argv[1] if len(sys.argv) > 1 else 'benchmarks/volatility-2026-11-01'))
