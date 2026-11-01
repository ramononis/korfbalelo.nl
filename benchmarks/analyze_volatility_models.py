#!/usr/bin/env python3
# AI generated: paired month bootstrap, seasonal summaries and comparison figure.
import csv
import gzip
from pathlib import Path
import sys
import numpy as np

METRICS = ['brier', 'logLoss', 'marginMae', 'teamScoreMae', 'marginGaussianNll', 'marginCoverage95']


def read(path):
    opener = path.open if path.exists() else lambda: gzip.open(str(path) + ".gz", "rt")
    with opener() as f:
        return list(csv.DictReader(f))


def write(path, rows):
    with path.open('w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)


def analyze(root):
    comparisons, seasons, years, periods = [], [], [], []
    for scenario in ['later', 'later-fitted', 'since-2010', 'since-2010-fitted']:
        if not (root / scenario / "summary.csv").exists():
            continue
        summaries = read(root / scenario / 'summary.csv')
        models = [r['id'] for r in summaries]
        data = {name: read(root / scenario / f'{name}-by-date.csv') for name in models}
        pairs = [('baseline', name) for name in models if name != 'baseline'] + [
            ('goal-margin-balanced-constant-ablation', 'goal-margin-balanced'),
            ('shared-probability', 'goal-margin-probability'),
            ('shared-balanced', 'goal-margin-balanced')]
        for reference, name in pairs:
            a, b = data[reference], data[name]
            assert [(r['date'], r['matches']) for r in a] == [(r['date'], r['matches']) for r in b]
            months = sorted({r['date'][:7] for r in a})
            counts, deltas = [], []
            for month in months:
                indices = [i for i, r in enumerate(a) if r['date'].startswith(month)]
                n = sum(int(a[i]['matches']) for i in indices)
                counts.append(n)
                deltas.append([sum((float(b[i][m]) - float(a[i][m])) * int(a[i]['matches'])
                                   for i in indices) for m in METRICS])
            counts, deltas = np.array(counts), np.array(deltas)
            rng = np.random.default_rng(20261101)
            selected = rng.integers(len(months), size=(4000, len(months)))
            samples = deltas[selected].sum(axis=1) / counts[selected].sum(axis=1)[:, None]
            for j, metric in enumerate(METRICS):
                lo, hi = np.quantile(samples[:, j], [.025, .975])
                comparisons.append(dict(scenario=scenario, reference=reference, model=name, metric=metric,
                    delta=deltas[:, j].sum() / counts.sum(), ci025=lo, ci975=hi, months=len(months)))
        for name, rows in data.items():
            grouped = {}
            for r in rows:
                year = int(r['date'][:4]) - (int(r['date'][5:7]) < 8)
                grouped.setdefault(year, []).append(r)
            for year, group in sorted(grouped.items()):
                n = sum(int(r['matches']) for r in group)
                seasons.append(dict(scenario=scenario, model=name, season=f'{year}/{str(year+1)[-2:]}', matches=n,
                    **{m: sum(float(r[m]) * int(r['matches']) for r in group) / n for m in METRICS}))
            calendar = sorted({int(r['date'][:4]) for r in rows})
            for year in calendar:
                group = [r for r in rows if int(r['date'][:4]) == year]
                n = sum(int(r['matches']) for r in group)
                years.append(dict(scenario=scenario, model=name, year=year, matches=n,
                    **{m: sum(float(r[m]) * int(r['matches']) for r in group) / n for m in METRICS}))
            if scenario.startswith('since-2010'):
                for start, end in [(2010, 2015), (2016, 2021), (2022, 2026), (2010, 2026)]:
                    group = [r for r in rows if start <= int(r['date'][:4]) <= end]
                    n = sum(int(r['matches']) for r in group)
                    periods.append(dict(scenario=scenario, model=name, period=f'{start}–{end}', matches=n,
                        **{m: sum(float(r[m]) * int(r['matches']) for r in group) / n for m in METRICS}))
    write(root / 'comparisons.csv', comparisons)
    write(root / 'seasons.csv', seasons)
    write(root / 'calendar-years.csv', years)
    write(root / 'periods.csv', periods)
    plot(root, comparisons)


def plot(root, comparisons):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib.ticker import FormatStrFormatter
    fig, axes = plt.subplots(1, 3, figsize=(15, 4.7), gridspec_kw={'width_ratios': [1.1, 1.1, 1.25]})
    primary = 'since-2010' if (root / 'since-2010').exists() else 'later'
    names = [('shared-balanced', 'Shared: score focused'),
             ('goal-margin-balanced', 'Adaptive: balanced'),
             ('goal-margin-balanced-constant-ablation', 'Shared: matched parameters')]
    colors = ['#4d87a2', '#d7862b', '#41783d']
    for ax, metric, title in zip(axes[:2], ['brier', 'marginGaussianNll'],
                                ['W/D/L Brier change', 'Gaussian goal-margin NLL change']):
        for i, ((name, label), color) in enumerate(zip(names, colors)):
            r = next(r for r in comparisons if r['scenario'] == primary and r['reference'] == 'baseline'
                     and r['model'] == name and r['metric'] == metric)
            ax.errorbar(r['delta'], i, xerr=[[r['delta'] - r['ci025']], [r['ci975'] - r['delta']]],
                        fmt='o', color=color, capsize=3)
        ax.axvline(0, color='#777777', linestyle='--', linewidth=1)
        ax.set_yticks(range(len(names)), [n[1] for n in names])
        ax.invert_yaxis(); ax.set_title(title); ax.set_xlabel('Difference from previous model; lower is better')
        ax.xaxis.set_major_formatter(FormatStrFormatter('%.3f'))
    ax = axes[2]
    for name, label, color in [('baseline', 'Previous volatility', '#777777'),
                               ('goal-margin-balanced', 'Adaptive volatility', '#d7862b')]:
        rows = [r for r in read(root / primary / f'{name}-teams.csv') if r['origins'] == '1'
                and r['lastMatch'] >= '2025-08-01']
        ax.scatter([int(r['games']) for r in rows], [float(r['sigma']) for r in rows],
                   label=label, color=color, alpha=.65, s=12)
    ax.axhline(.06, color='#41783d', linestyle='--', linewidth=1, label='Shared volatility 0.06')
    ax.set(title='Volatility versus recorded match count', xlabel='Recorded matches', ylabel='Volatility σ')
    ax.legend(frameon=False, fontsize=8)
    for ax in axes:
        ax.spines[['top', 'right']].set_visible(False); ax.grid(alpha=.15)
    count = int(read(root / primary / 'summary.csv')[0]['matches'])
    months = len({r['date'][:7] for r in read(root / primary / 'baseline-by-date.csv')})
    window = '2010–2026' if primary == 'since-2010' else '2022–2026'
    fig.suptitle(f'Recalibrated volatility models · {count:,} matches · {window}', fontsize=13)
    fig.text(.5, -.02, f'Paired calendar-month bootstrap: 4,000 resamples, {months} months. Starting-rating priors disabled. '
             'Retrospective evaluation; not an untouched prospective test. AI-generated analysis.', ha='center', fontsize=8)
    fig.tight_layout(); fig.savefig(root / 'comparison.png', dpi=180, bbox_inches='tight'); plt.close(fig)
    annual = [r for r in read(root / 'calendar-years.csv') if r['scenario'] == primary]
    base = {int(r['year']): r for r in annual if r['model'] == 'baseline'}
    fig, axes = plt.subplots(2, 1, figsize=(10, 6), sharex=True)
    for (name, label), color in zip(names, colors):
        selected = sorted([r for r in annual if r['model'] == name], key=lambda r: int(r['year']))
        for ax, metric, title in zip(axes, ['brier', 'teamScoreMae'], ['W/D/L Brier', 'Team-score MAE (goals)']):
            ax.plot([int(r['year']) for r in selected],
                    [float(r[metric]) - float(base[int(r['year'])][metric]) for r in selected],
                    marker='o', markersize=3, color=color, label=label)
            ax.axhline(0, color='#777777', linestyle='--', linewidth=1)
            ax.set_ylabel(title + ' change'); ax.grid(alpha=.15)
            ax.spines[['top', 'right']].set_visible(False)
    axes[0].legend(frameon=False, fontsize=9, ncol=3)
    axes[1].set_xticks(sorted(base)); axes[1].set_xlabel('Calendar year (2026 through 17 October)')
    fig.suptitle('Year-by-year change from previous model · lower is better')
    fig.tight_layout(); fig.savefig(root / 'annual-comparison.png', dpi=180, bbox_inches='tight'); plt.close(fig)


if __name__ == '__main__':
    analyze(Path(sys.argv[1] if len(sys.argv) > 1 else 'benchmarks/volatility-model-comparison-2026-11-01'))
