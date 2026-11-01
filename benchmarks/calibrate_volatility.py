#!/usr/bin/env python3
# AI generated: deterministic local interaction search and pre-evaluation finalist freezing.
import copy
import csv
import json
from pathlib import Path
import random
import sys


def load(root):
    records = []
    for stage in ('search', 'refine', 'refine2', 'polish'):
        path = root / stage / 'summary.csv'
        if path.exists():
            with path.open() as f:
                records.extend(csv.DictReader(f))
    base = next(r for r in records if r['family'] == 'baseline')
    def objective(r):
        return sum(w * float(r[k]) / float(base[k]) for k, w in
                   [('brier', .2), ('logLoss', .2), ('recentBrier', .3), ('recentLogLoss', .3)])
    seeds = []
    for family in ('shared', 'goal-margin'):
        candidates = [r for r in records if r['family'] == family and
                      (family != 'goal-margin' or json.loads(r['configJson'])['model']['volatility']['learningRate'] > 0)]
        safe = [r for r in candidates if float(r['marginMae']) <= float(base['marginMae']) * 1.002 and
                float(r['teamScoreMae']) <= float(base['teamScoreMae']) * 1.002 and
                float(r['recentMarginMae']) <= float(base['recentMarginMae']) * 1.005]
        for role, group in [('probability', candidates), ('balanced', safe)]:
            chosen = min(group, key=objective)
            seeds.append(dict(id=f'{family}-{role}', family=family, config=json.loads(chosen['configJson'])))
    return dict(id='baseline', family='baseline', config=json.loads(base['configJson'])), seeds


def polish(root):
    base, seeds = load(root)
    trials = [base] + seeds
    rng = random.Random(20261101)
    for seed in seeds:
        for i in range(40):
            config = copy.deepcopy(seed['config'])
            m = config['model']; v = m['volatility']
            changes = {
                'sigma': lambda: v.update(initial=round(v['initial'] * rng.choice([.9, 1.1]), 4)),
                'margin': lambda: m.update(marginRatingScale=m['marginRatingScale'] + rng.choice([-20, 20])),
                'slope': lambda: m.update(scoreSdSlope=round(m['scoreSdSlope'] + rng.choice([-.015, .015]), 4)),
                'intercept': lambda: m.update(scoreSdIntercept=round(m['scoreSdIntercept'] + rng.choice([-.2, .2]), 4)),
                'pace': lambda: m.update(scoreSpeedInv=rng.choice([80, 100, 130, 170, 220, 260])),
                'home': lambda: m.update(initialHomeAdvantage=rng.choice([20, 35, 50])),
                'homeSpeed': lambda: m.update(homeAdvantageSpeed=rng.choice([0, .015625, .0625])),
                'rd': lambda: m.update(rdMax=rng.choice([300, 350, 450])),
                'initialRd': lambda: m.update(initialRdFraction=rng.choice([.5, .75, 1.])),
                'scoreTweak': lambda: config['scoreRating'].update(learningRate=rng.choice([.0005, .001, .002])),
                'season': lambda: config['seasonality'].update(learningRate=rng.choice([.001, .0025, .005, .01])),
            }
            if seed['family'] == 'goal-margin':
                changes.update(adaptiveRate=lambda: v.update(learningRate=rng.choice([1, 2, 4, 8, 16, 32])),
                               memory=lambda: v.update(halfLifeDays=rng.choice([90, 180, 365, 730, 1825])))
            for key in rng.sample(list(changes), rng.choice([1, 2, 3])):
                changes[key]()
            trials.append(dict(id=f"{seed['id']}-p{i}", family=seed['family'], config=config))
    (root / 'polish-input.json').write_text(json.dumps(trials, indent=2) + '\n')


def freeze(root):
    base, seeds = load(root)
    trials = [base] + seeds
    for seed in [s for s in seeds if s['id'].endswith('balanced')]:
        config = copy.deepcopy(seed['config'])
        config['model']['volatility'] = copy.deepcopy(base['config']['model']['volatility'])
        trials.append(dict(id=seed['id'] + '-legacy-ablation', family='control', config=config))
    config = copy.deepcopy(base['config'])
    config['model']['volatility']['mode'] = 'SHARED'
    trials.append(dict(id='shared-untuned', family='control', config=config))
    config = copy.deepcopy(base['config'])
    config['model']['volatility']['mode'] = 'GOAL_MARGIN'
    trials.append(dict(id='goal-margin-untuned', family='control', config=config))
    (root / 'finalists.json').write_text(json.dumps(trials, indent=2) + '\n')
    print(json.dumps(seeds, indent=2))


def prepare_controls(root):
    trials = json.loads((root / 'finalists.json').read_text())
    base = next(x for x in trials if x['id'] == 'goal-margin-balanced')
    for sigma in [.04, .05, .06, .07, .08, .09]:
        trial = copy.deepcopy(base)
        trial.update(id=f'matched-constant-{sigma}', family='control')
        trial['config']['model']['volatility'].update(mode='SHARED', initial=sigma)
        trials.append(trial)
    (root / 'development-finalists.json').write_text(json.dumps(trials, indent=2) + '\n')


def freeze_control(root):
    with (root / 'development-finalists/summary.csv').open() as f:
        records = list(csv.DictReader(f))
    base = next(r for r in records if r['id'] == 'baseline')
    def objective(r):
        return sum(w * float(r[k]) / float(base[k]) for k, w in
                   [('brier', .2), ('logLoss', .2), ('recentBrier', .3), ('recentLogLoss', .3)])
    chosen = min([r for r in records if r['id'].startswith('matched-constant-')], key=objective)
    trials = [x for x in json.loads((root / 'finalists.json').read_text())
              if x['id'] != 'goal-margin-balanced-constant-ablation']
    trials.append(dict(id='goal-margin-balanced-constant-ablation', family='control',
                       config=json.loads(chosen['configJson'])))
    (root / 'finalists.json').write_text(json.dumps(trials, indent=2) + '\n')
    (root / 'selection.json').write_text(json.dumps(dict(ai_generated=True, searchSeed=20261101,
        evaluationStart='2022-08-01', developmentEnd='2022-07-31', fittedStartingRatingsDuringSearch=False,
        probabilityObjective=dict(brier=.2, logLoss=.2, recentBrier=.3, recentLogLoss=.3),
        recentFrom='2012-08-01', balancedGuard=dict(marginMaeMaxRatio=1.002,
        teamScoreMaeMaxRatio=1.002, recentMarginMaeMaxRatio=1.005), matchedConstant=chosen['id']), indent=2) + '\n')


if __name__ == '__main__':
    root = Path(sys.argv[2] if len(sys.argv) > 2 else 'build/volatility-calibration')
    {'polish': polish, 'freeze': freeze, 'prepare-controls': prepare_controls,
     'freeze-control': freeze_control}[sys.argv[1]](root)
