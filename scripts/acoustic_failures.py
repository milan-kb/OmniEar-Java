"""Explain observed per-file direct/raw failures from actual paired replay rows."""
import csv
import sys

baseline = list(csv.DictReader(open(sys.argv[1]), delimiter='\t'))
candidate = list(csv.DictReader(open(sys.argv[2]), delimiter='\t'))
old_files = [r['file'] for r in baseline if r['kind'] == 'DIRECT']
new_files = [r['file'] for r in candidate if r['kind'] == 'DIRECT']
assert old_files and len(set(old_files)) == len(old_files), 'baseline must have one direct row per file'
assert set(old_files) == set(new_files) and len(old_files) == len(new_files), 'paired replays must cover identical files'
print('file\ttruth\tbaseline_direct\tcandidate_direct\tbaseline_raw_correct_alert\tcandidate_raw_correct_alert\tcandidate_raw_diagnosis')
for direct in (r for r in candidate if r['kind'] == 'DIRECT'):
    path, truth = direct['file'], direct['truth']
    old = next(r for r in baseline if r['kind'] == 'DIRECT' and r['file'] == path)
    events = [r for r in candidate if r['file'] == path and r['kind'] == 'EVENT' and r['scenario'] == 'raw']
    prior = [r for r in baseline if r['file'] == path and r['kind'] == 'EVENT' and r['scenario'] == 'raw']
    hit = truth != 'background' and any(r['alert'] == 'true' and r['predicted'] == truth for r in events)
    old_hit = truth != 'background' and any(r['alert'] == 'true' and r['predicted'] == truth for r in prior)
    if truth == 'background':
        diagnosis = 'false alert: ' + ','.join(sorted({r['predicted'] for r in events if r['alert'] == 'true'})) if any(r['alert'] == 'true' for r in events) else 'no background alert'
    elif hit:
        diagnosis = 'correct alert emitted'
    elif not events:
        diagnosis = 'no classification window emitted'
    elif not any(r['predicted'] == truth for r in events):
        diagnosis = 'classification: all windows preferred ' + ','.join(sorted({r['predicted'] for r in events}))
    else:
        diagnosis = 'truth predicted but rejected: ' + ','.join(sorted({r['status'] for r in events if r['predicted'] == truth}))
    print('\t'.join([path, truth, old['predicted'], direct['predicted'], str(old_hit), str(hit), diagnosis]))
