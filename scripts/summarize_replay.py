"""Summarize auditable ReplayEvaluation TSV, with weak-label latency explicitly named."""
import collections
import csv
import statistics
import sys

CLASSES = ['scream_distress', 'explosion', 'impact_crash', 'siren_traffic', 'background']
rows = list(csv.DictReader(open(sys.argv[1]), delimiter='\t'))
for scenario in ['direct', 'raw', 'quiet', 'low', 'high', 'bed', 'multi', 'multi-gap']:
    data = [r for r in rows if r['scenario'] == scenario]
    records = [r for r in data if r['kind'] == ('DIRECT' if scenario == 'direct' else 'RECORD')]
    events = collections.defaultdict(list)
    for r in data:
        if r['kind'] in ('EVENT', 'DIRECT'):
            events[(r['file'], r['start'])].append(r)
    matrix = [[0] * 6 for _ in CLASSES]
    alert_matrix = [[0] * 6 for _ in CLASSES]
    targets = hits = false = bg_files = bg_alert_files = correct_alerts = alert_count = no_events = 0
    bg_seconds = total_seconds = 0
    latencies = []
    for r in records:
        all_emitted = events[(r['file'], r['start'])]
        emitted = [e for e in all_emitted if float(e['emission']) >= float(r['start'])]
        all_alerts = [e for e in all_emitted if e['alert'] == 'true']
        alerts = [e for e in emitted if e['alert'] == 'true']
        truth = CLASSES.index(r['truth'])
        matrix[truth][CLASSES.index(emitted[0]['predicted']) if emitted else 5] += 1
        alert_matrix[truth][CLASSES.index(alerts[0]['predicted']) if alerts else 5] += 1
        no_events += not emitted
        seconds = float(r['end']) - (0 if scenario in ('quiet', 'low', 'high', 'bed') else float(r['start']))
        total_seconds += seconds
        alert_count += len(all_alerts)
        correct_alerts += sum(e['predicted'] == r['truth'] and truth != 4 for e in alerts)
        if truth == 4:
            bg_files += 1
            bg_alert_files += bool(all_alerts)
            bg_seconds += seconds
            false += len(all_alerts)
        else:
            if scenario in ('quiet', 'low', 'high', 'bed'):
                bg_seconds += float(r['start'])
                false += len(all_alerts) - len(alerts)
            targets += 1
            routed = [e for e in alerts if e['predicted'] == r['truth'] and float(e['emission']) >= float(r['start'])]
            hits += bool(routed)
            if routed:
                latencies.append(float(routed[0]['emission']) - float(r['start']))
    print(f'\n{scenario}: recordings={len(records)} audio={total_seconds:.2f}s')
    print('First emitted classification matrix; columns=' + ','.join(CLASSES + ['NO_EVENT']))
    for c, row in zip(CLASSES, matrix):
        print(c, row)
    for i, c in enumerate(CLASSES):
        predicted = sum(row[i] for row in matrix)
        support = sum(matrix[i])
        print(f'{c}: precision={matrix[i][i]}/{predicted}; recall={matrix[i][i]}/{support}')
    print('First routed alert matrix (no alert includes rejection/duplicate suppression):')
    for c, row in zip(CLASSES, alert_matrix):
        print(c, row)
    print(f'Correct first labels={sum(matrix[i][i] for i in range(5))}/{len(records)}; no emitted classification={no_events}')
    print(f'Correctly routed target-recording recall={hits}/{targets}; alert classification precision={correct_alerts}/{alert_count}')
    if scenario == 'direct':
        print(f'Background clips passing alert gates={false}/{bg_files}; no continuous-time rate or latency for direct classification.')
    else:
        print(f'Background false alerts (including target preludes)={false}; background files with alerts={bg_alert_files}/{bg_files}; exposure={bg_seconds:.2f}s; false alerts/hour={false * 3600 / bg_seconds if bg_seconds else 0:.2f}')
        print(f'File-insertion-to-correct-alert latency proxy: n={len(latencies)}, median={statistics.median(latencies) if latencies else None}, max={max(latencies) if latencies else None}')
print('\nSimulated replay, not physical microphone event validation. Weak labels cannot establish actual acoustic onset or event-instance recall.')
