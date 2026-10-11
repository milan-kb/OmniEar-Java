"""Select on baseline development decisions only, preserving target-interval recall."""
import csv

rows = list(csv.DictReader(open('evaluation/rejection-development-probe.tsv'), delimiter='\t'))
assert all('/rejection-final/' not in r['file'] for r in rows)
alerts = [r for r in rows if r['alert'] == 'true']
def key(r):
    return r['scenario'], r['file'], r['start']
def correct(r):
    return r['truth'] != 'background' and r['truth'] == r['predicted'] and float(r['emission']) >= float(r['start'])
def false(r):
    return r['truth'] == 'background' or float(r['emission']) < float(r['start'])
baseline = {key(r) for r in alerts if correct(r)}
print('Baseline correctly detected intervals=', len(baseline), 'false alerts=', sum(map(false, alerts)))
for feature, thresholds, direction in [
    ('distance', [.6, .8, 1., 1.2, 1.5, 2.], 'gt'),
    ('winner_distance', [.6, .8, 1., 1.2, 1.5, 2.], 'gt'),
    ('group_radius_ratio', [.75, 1., 1.25, 1.5, 2.], 'gt'),
    ('background_ratio', [.5, .6, .7, .8, .9, 1.], 'lt'),
    ('hard_negative_ratio', [.5, .6, .7, .8, .9, 1.], 'lt')]:
    for threshold in thresholds:
        kept = [r for r in alerts if not (float(r[feature]) > threshold if direction == 'gt' else float(r[feature]) < threshold)]
        detected = {key(r) for r in kept if correct(r)}
        print(feature, threshold, 'lost=', len(baseline - detected), 'false=', sum(map(false, kept)))
print('\nPer-predicted-class relative rejection (do not change explosions without fresh final coverage):')
for category in ['scream_distress', 'impact_crash', 'siren_traffic']:
    for feature in ['background_ratio', 'hard_negative_ratio', 'group_radius_ratio']:
        choices = []
        for threshold in ([.5, .6, .7, .8, .9, 1.] if feature.endswith('ratio') and feature != 'group_radius_ratio' else [.75, 1., 1.25, 1.5, 2.]):
            kept = [r for r in alerts if r['predicted'] != category or not (float(r[feature]) > threshold if feature == 'group_radius_ratio' else float(r[feature]) < threshold)]
            detected = {key(r) for r in kept if correct(r)}
            if not baseline - detected:
                choices.append((sum(map(false, kept)), threshold))
        print(category, feature, sorted(choices))

# Consecutive-capture confirmation: resetting at each isolated replay, continuous across multi intervals.
kept = []
previous = {}
for r in alerts:
    if r['kind'] == 'DIRECT':
        continue
    stream = ('multi',) if r['scenario'].startswith('multi') else (r['scenario'], r['file'])
    if previous.get(stream) == r['predicted']:
        kept.append(r)
    previous[stream] = r['predicted']
stream_baseline = {key(r) for r in alerts if r['kind'] == 'EVENT' and correct(r)}
print('Two consecutive alert-capture labels (streaming only): lost=', len(stream_baseline - {key(r) for r in kept if correct(r)}), 'of', len(stream_baseline), 'false=', sum(map(false, kept)))
