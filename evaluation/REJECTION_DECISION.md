# Background-conflict candidate rejected; baseline retained

Baseline `747f7e8`, fresh data freeze `9ac2ab0`, candidate freeze `4e4e17f`,
rollback `d9befdd`. Candidate and baseline have **identical final predictions**;
the required streaming false-alert reduction did not occur. No final tuning was
performed. Runtime, tests and build return to the original accepted version.

## Root causes and development evidence

The 99 development recordings comprise all previously inspected test data.
`rejection-development-probe.tsv` records the exact original capture decisions,
with winning-class distance, background/winner distance ratio, hard-negative
ratio, group-calibrated radius ratio and nearest negative source/window.

1. **Many errors are near the training distribution.** Rainfall on a hat is
   called explosion with vote 0.784 and distance 0.385; fireworks with vote 0.955
   and distance 0.230; bells with vote 0.941 and distance 0.399 (scenario-dependent
   examples, not independent samples). An absolute distance cutoff of 1.0 removes
   five background alerts but loses ten baseline-correct recording/scenario
   intervals. Cutoff 2.0 preserves recall but removes no false alerts.
2. **Vote share is not calibrated confidence.** The nearest reported neighbor
   can be background even when inverse-frequency weighted votes select a threat.
   Counts are 18 explosions versus 40 backgrounds: equally distant explosion
   neighbors get 40/18 times the weight. Increasing background training to 82
   increases that relative factor to 82/18, while also changing feature scales
   and neighborhoods. A larger negative class therefore need not reduce alerts.
   This is a mechanism in the code, not proof that it causes every observed error.
3. **Feature overlap and lost context.** RMS/MFCC summaries retain gain effects
   and reduce frame order to mean/max/deviation. They cannot reliably distinguish
   a door/hammer/firework transient from impact/explosion, or bark/cheer from a
   scream. Among 90 streamed development background alerts, 50 are explosions,
   27 screams and 13 impacts. The quiet-prelude and gain comparisons expose
   sensitivity to context. The existing energy gate's warmup and rolling baseline
   also hide true events, especially sustained sirens; changing alert rejection
   cannot recover a window that was never classified.

### Investigated rejection alternatives

- Winning-class distance instead of any-neighbor distance: cutoff 1.0 loses
  11 correct intervals; recall-preserving tested cutoffs reject no false alerts.
- Source-group-out nearest-positive 95th-percentile radii: 1.178 scream, 0.990
  explosion, 0.784 impact, 1.756 siren, 1.049 background. Neighbor references
  exclude the query source group; scales are fit once on all training data.
  This is a training-only distance diagnostic, not nested validation or a
  probability calibration. Radius multiplier 1.0 loses 15 correct intervals;
  1.5 preserves them but removes no false alerts.
- A negative reference bank uses 82 real training/background recordings and
  their quiet-prelude captures (166 vectors total). References include the 42
  previously acquired hard negatives but no development/final features. Global
  negative/winner ratio 0.8 removes 20 false alerts but loses four correct
  intervals; 0.5 preserves recall and removes only one. Per-class search favored
  existing-background references over the larger bank for screams and impacts.
- Two consecutive alert-capture labels remove many isolated false alerts but
  lose 125/164 streaming baseline-correct intervals. This diagnostic resets for
  each isolated file and persists through multi-file gaps, ignores non-alert
  captures and has no timeout. It is a permissive confirmation screen, not a
  deployed debounce algorithm. Stricter reset/timeout rules cannot recover its
  missed one-capture events without changing capture scheduling.

Threshold grids and results: `scripts/select_rejection.py` and
`rejection-selection.txt`. Final files are explicitly excluded from that script.

### Frozen relative-background candidate

For predicted scream, reject alert eligibility when nearest background distance
is strictly below 0.9 × nearest scream distance; for impact, use 1.0 × nearest
impact distance. Other labels, training, scales, features, k=7, vote gates and
scheduling stay fixed. Retain predicted class/vote and persist an explicit
background-conflict rejection reason. No final explosion samples were available,
so no explosion-specific rejection rule was selected.

| Development scenario | Baseline → candidate routed targets (/61) | Baseline → candidate background alerts |
|---|---:|---:|
| Direct | 35 → 35 | 9 → 7 |
| Raw | 7 → 7 | 2 → 2 |
| Quiet | 39 → 39 | 24 → 23 |
| Low gain | 35 → 35 | 10 → 9 |
| High gain | 40 → 40 | 25 → 25 |
| Background bed | 21 → 21 | 11 → 11 |
| Multi + gaps | 22 → 22 | 18 → 17 |

All 199 baseline-correct direct/streamed intervals survive, without delaying
their alerts. Five background alerts and three wrongly routed target alerts
are suppressed. Raw precision/latency/abstentions are unchanged; full scenario
precision, latency and matrices are in the paired development summaries.

## Final decision

The 48 new recordings have 48 unused uploaders: 8 screams, 8 breakages, 2 sirens,
30 backgrounds, no verified explosions. All 671 TSV rows are identical, not
merely rounded headline metrics. Direct accuracy is 25/48; raw target recall
1/18, precision 1/4, three false alerts in 329.96 s (32.73/hour), 41/48 classifier
abstentions. Combined continuous stream recall is 9/18, precision 11/29, 17 false
alerts in 473.96 s (129.13/hour). See `../TEST_RESULTS.md` and paired summaries.

Equality fails the frozen requirement to reduce streaming background alerts.
The gate is reverted rather than shipping extra logic with no measured final
benefit. This final set is now inspected and cannot support another untouched
evaluation. No new thresholds or alternative candidates were tried on it.

## Supplementary development-only hard-negative training ablation

After the final decision, completed a fixed 212-recording ablation: original
170 plus all 42 existing hard-negative recordings, same k=7 and original runtime.
This is distinct from the earlier 217-file all-class expansion. No fresh-final
inference or parameter search was performed for this ablation.

Adding negatives reduced background-bed false alerts 11 → 5 and combined-stream
false alerts 18 → 13, but increased low-gain false alerts 10 → 12 and reduced
continuous recall 22/61 → 21/61. Nine individual baseline-correct intervals were
lost across scenarios, despite gains elsewhere (including direct recall
35/61 → 39/61). It fails the development acceptance rule. Class priors/scaling
changes and additional examples must be evaluated jointly; more negatives alone
are not a demonstrated fix. Script and complete TSV/summary are retained.

## Limitations

Metadata curation is not a listening-panel audit. Known uploader/source groups
and decoded duplicates are separated, but unknown re-encodings may exist.
Screams are mostly acted; impacts are breakages, not vehicle crashes. Explosion
final recall is unmeasured, and two sirens offer little support. Replays repeat
recordings under digital transformations, not independent hours of field audio.
Latency is from file insertion, excludes misses, and is not annotated onset.
The microphone diagnostic only validates opening/capture/release. No reliable
emergency-detection claim follows from these results.
