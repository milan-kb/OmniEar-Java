"""Development-only selective operating points, not probability calibration.

Input is AcousticStudy's rejection-dev TSV, never acoustic-final. Repeated gain/
prelude scenarios are not independent data. Raw vote share stays a diagnostic;
the gate tests class-neighbor radius and separation from real backgrounds.
"""
import csv
import sys

CLASSES = ['scream_distress', 'explosion', 'impact_crash', 'siren_traffic']
rows = list(csv.DictReader(open(sys.argv[1]), delimiter='\t'))
assert rows and all('acoustic-final' not in r['file'] and 'rejection-final' not in r['file'] for r in rows)


def measure(category, radius, ratio):
    last = {}
    hits = set()
    false_background = set()
    wrong = set()
    total = 0
    for n, r in enumerate(rows):
        if r['kind'] not in ('EVENT', 'DIRECT') or r['predicted'] != category:
            continue
        if float(r['category_distance']) > radius or float(r['background_ratio']) > ratio:
            continue
        scenario, path = r['scenario'], r['file']
        time = float(r['emission'])
        key = (scenario if scenario.startswith('multi') else (scenario, path), category)
        if scenario.startswith('multi'):
            key = ('continuous', category)
        duplicate = scenario != 'direct' and time - last.get(key, -1e9) <= 5
        last[key] = time
        if duplicate:
            continue
        total += 1
        correct = r['truth'] == category and time >= float(r['start'])
        if correct:
            hits.add((scenario, path))
        elif r['truth'] == 'background' or time < float(r['start']):
            false_background.add(n)
        else:
            wrong.add(n)
    raw = sum(s == 'raw' for s, _ in hits)
    return raw, len(hits), len(false_background), len(wrong), total


for category in CLASSES:
    selected = {'scream_distress': (1.5, .8), 'explosion': (.3, .7), 'impact_crash': (1, .8), 'siren_traffic': (.75, .7)}[category]
    print(category, 'frozen point', selected, 'raw hits / scenario hits / background alerts / wrong targets / all alerts:', measure(category, *selected))
    choices = []
    for radius in [.3, .5, .75, 1, 1.25, 1.5, 2]:
        for ratio in [.4, .5, .6, .7, .8, .9, 1]:
            raw, hits, false, wrong, total = measure(category, radius, ratio)
            # No new background alerts across the development scenarios, and
            # reject candidates with more than 10% wrongly routed target alerts.
            if false == 0 and (not total or wrong / total <= .1):
                choices.append((raw, hits, -wrong, -radius, -ratio, total))
    print('Zero-background alternatives (often reject virtually everything):')
    for raw, hits, wrong, radius, ratio, total in sorted(choices, reverse=True)[:8]:
        print(f' radius={-radius:g} ratio={-ratio:g} raw_hits={raw} scenario_hits={hits} wrong={-wrong} total_alerts={total}')
