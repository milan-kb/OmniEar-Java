"""Development only: choose gates with zero background alerts across all gain/prelude scenarios."""
import collections
import csv
import itertools
import sys

classes = ['scream_distress', 'explosion', 'impact_crash', 'siren_traffic']
groups = collections.defaultdict(list)
for r in csv.DictReader(open(sys.argv[1]), delimiter='\t'):
    assert '/final-test/' not in r['file']
    groups[(r['scenario'], r['file'])].append(r)

for mode in ['onset', 'periodic', 'hybrid']:
    print('\nMODE', mode, sys.argv[1])
    for category in classes:
        choices = []
        for vote, distance, confirmations in itertools.product([.7, .8, .9, .95, .99], [.3, .4, .5, .6, .7, .8, 1., 1.2], [1, 2]):
            if category == 'siren_traffic' and vote < .8:
                continue  # Retain the original category's minimum alert gate.
            hits = false = 0
            for key, rows in groups.items():
                streak = 0
                detected = False
                for r in rows:
                    if mode == 'onset' and r['onset'] != 'true':
                        continue
                    if mode == 'hybrid' and r['onset'] != 'true' and abs(float(r['time']) % 1) > .001:
                        continue
                    good = r['category'] == category and float(r['vote']) >= vote and float(r['distance']) <= distance
                    streak = streak + 1 if good else 0
                    if streak >= confirmations:
                        detected = True
                hits += detected and rows[0]['truth'] == category
                false += detected and rows[0]['truth'] == 'background'
            if false == 0:
                choices.append((hits, vote, -distance, confirmations))
        best = max(choices)
        print(category, 'hits across repeated scenarios=', best[0], 'vote>=', best[1], 'distance<=', -best[2], 'confirmations=', best[3])
