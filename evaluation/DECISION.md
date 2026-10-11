# Frozen development decision

Dataset frozen at `199361d`; final-test predictions have not been consulted.
Runtime decision: periodic one-second normalized windows every 0.5 seconds;
retain k=7 and full inverse-class-frequency voting. Training has 217 genuine
recordings. No augmented/gain-varied replay was imported into training.

## Diagnosis

The original 20-block warmup swallowed short startup events. Its rolling mean
included event energy, so sustained sirens raised their own trigger threshold.
The 6×/0.02 gate missed low-level sounds. A 1.3 s post-trigger wait plus a 3.5 s
ring supplied pre-onset quiet unlike the loudest 2.5 s training clips. Original
vote-only alert gates accepted distant neighbors and misclassified backgrounds.

## Compared on development only

The old 50 inspected files plus eight additional backgrounds give 58 recordings.
`WindowDevelopment.java` emits every candidate window score for raw/quiet/0.25×/
clipped-2×/real-background-bed replay. `select_stream_gates.py` searches the printed
vote/distance grid, with one/two supported decisions, constrained to zero alerts
on development background recordings in those scenarios. No file is counted as
a new independent recording because it was replayed at another gain.

`strategy-development.txt` records adaptive-onset-only (2× fast energy rise over
EMA, floor 0.002), periodic and hybrid (onset neighborhoods plus 1 Hz fallback)
comparisons. Under those candidate gates they recover respectively 82, 108 and
107 of 200 target-recording/scenario combinations. Periodic is selected: 2 Hz
maximum during non-silence, better sustained-siren coverage, simpler scheduling.
The original 2.5 s transform, normalized one-second audio padded back to 2.5 s,
and square-root class balancing were explored and rejected. Only the selected
transform and original k-NN voting remain in the runtime.

Evidence gates: scream/breakage vote ≥0.70, distance ≤0.80; siren ≥0.80/≤0.70;
explosion ≥0.99/≤0.30 plus two consecutive decisions in streams. Apply support
gates to direct alerts too. Keep the original category minima/CSV invariants.
Suppress repeated supported decisions until a 3 s gap in category support.
Predicted labels remain visible even when an alert is rejected.

## Acceptance evidence

Paired full-runtime development replay, same input and sample-clock protocol:

| Scenario | Correctly routed target recordings, old → selected (/40) | Background false alerts, old → selected |
|---|---:|---:|
| Raw | 4 → 22 | 0 → 0 |
| Quiet prelude | 24 → 22 | 10 → 0 |
| 0.25× gain | 22 → 22 | 2 → 0 |
| 2× gain with clipping | 25 → 22 | 12 → 0 |
| Real background bed | 16 → 20 | 0 → 0 |
| Continuous file intervals | 15 → 21 | 2 → 1 |
| Continuous inter-file background | N/A | 4 → 3 |

Direct classification is 40/58 for both models; direct background alerts fall
3/18 → 0/18. Background label recall remains poor (selected 7/18). In raw replay,
first-decision label accuracy rises 5/58 → 30/58 and classifier abstentions fall
49 → 0; **alert abstentions still exist**. Correct raw alerts cover 6/10 screams,
0/10 explosions, 10/10 breakages and 6/10 sirens. The first-alert matrix can differ
from any-correct-alert recall when an earlier alert has the wrong category.

Quiet/high-gain recall decreases slightly in exchange for eliminating background
alerts. Explosion alert recall is zero on development at the conservative gate:
this is a known unresolved limitation, not a successful explosion detector.
Raw insertion-to-correct-alert median is 6.6 → 2.0 s, with candidate maximum
22.5 s; these are file-insertion proxies, not annotated acoustic-onset latency.

Complete confusion matrices, precision/recall, actual exposure and per-decision
predictions are in `stream-{baseline,candidate}-development.{txt,tsv}`. New final
evaluation is report-only: no subsequent threshold/model tuning on those files.
