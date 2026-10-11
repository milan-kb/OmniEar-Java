"""Runnable audit of saved rejection-study predictions and summary accounting."""
import csv
from pathlib import Path
import subprocess
import sys


def rows(stem):
    with Path(f'evaluation/{stem}.tsv').open() as source:
        return list(csv.DictReader(source, delimiter='\t'))


for stem in ['rejection-development-baseline', 'rejection-development-candidate',
             'rejection-development-hard-negative', 'rejection-final-baseline',
             'rejection-final-candidate']:
    source = 'rejection-development-probe' if stem == 'rejection-development-baseline' else stem
    output = subprocess.check_output([sys.executable, 'scripts/summarize_replay.py', f'evaluation/{source}.tsv'])
    assert output == Path(f'evaluation/{stem}.txt').read_bytes(), stem

baseline = rows('rejection-development-probe')
candidate = rows('rejection-development-candidate')
assert len(baseline) == len(candidate)
removed = []
for before, after in zip(baseline, candidate):
    assert '/rejection-final/' not in before['file']
    # Only alert eligibility may change; classifications and emission times must not.
    assert all(before[k] == v for k, v in after.items() if k != 'alert')
    if before['alert'] != after['alert']:
        assert before['alert'] == 'true' and after['alert'] == 'false'
        assert before['truth'] != before['predicted'] or float(before['emission']) < float(before['start'])
        removed.append(before)
assert len(removed) == 8
assert sum(r['truth'] == 'background' for r in removed) == 5
assert rows('rejection-final-baseline') == rows('rejection-final-candidate')
assert all('/rejection-final/' not in r['file'] for r in rows('rejection-development-hard-negative'))
print('PASS: five summaries reproduce; development removes five false background and three wrong-target alerts without losing/delaying correct alerts; final has no improvement')
