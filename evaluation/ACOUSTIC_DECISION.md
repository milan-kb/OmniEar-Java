# Acoustic repair decision — candidate freeze, 2026-10-16

Baseline `48da665`; metadata-only fresh evaluation freeze `102e86f`.
This document records the development decision **before fresh-final inference**.
The final outcome will be appended, not used to retune this candidate.

## Root causes traced through the actual app

`AcousticTrace.java` and `acoustic-baseline-demo.tsv` trace all ten selected demo
recordings. **None reaches classification through streaming**. Seven clips have
their only above-gate blocks during the two-second blind calibration; after that
the six-times-mean threshold, contaminated by foreground energy, suppresses them.
The two sirens never cross the threshold; the quiet wind also stays below 0.02.
Specifically: 264918/249745 screams, 256296/426167 explosions, 320167/367624
breakages and background 110612 have startup above-gate blocks but no eligible
post-calibration block. Sirens 238228/378257 and wind 381853 have none at all.
Short 367624 ends at 1.20 s; 426167 lasts only 2.00 s. This is not a Swing issue.

The direct classifications bypass those failures:

| File | Baseline direct outcome and cause |
|---|---|
| 264918 | Correct scream, vote .716: no streaming window; direct alert only. |
| 249745 | Scream → siren, vote .637, distance 1.395; pooled spectral/MFCC neighbors confuse pitched vocal/siren structure, then the .80 vote gate rejects it. |
| 256296 | Correct explosion, vote .764; startup event contaminates baseline. |
| 426167 | Explosion → background, vote .500; weak/noisy short padded clip's nearest-neighbor geometry is not specific to explosion. |
| 320167 | Correct impact, unanimous votes; onset lost during warmup. |
| 367624 | Impact → explosion, vote .629; short transient/padding ambiguity plus rare-class inverse-frequency weighting; old .65 gate suppresses it. |
| 238228 | Correct sustained siren, vote .899; no sixfold energy increase. |
| 378257 | Quiet siren → background, vote .683; level/ambience dominated features and an adaptive gate up to .111 RMS. |
| 110612 | Correct background; startup suppression happened to prevent an event. |
| 381853 | Wind → scream, vote .562; low-level broadband feature overlap, yet the old .52 vote gate routes a false direct alert. |

FFT bins and all 13 MFCCs agree with an independent O(N²) DFT + mel/DCT
reference in the new tests. There was no demonstrated FFT/MFCC formula bug.
Training-only standard deviations were already correct. **Level dependence,
silent-padding statistics, weak temporal pooling, very few explosion sources,
and inadequate background coverage** are the relevant classifier limitations.

Old capture used only 1.3 s after onset and selected a loudest 2.5 s region from
3.5 s of context. Training instead selected full 2.5 s clips. Pre-onset silence
therefore changes features. Raw vote share is neighbor agreement, not calibrated
probability; its original per-class cutoffs had no development operating-point
justification for a more frequent analysis schedule.

MonitorService already persists before delivering events; Dashboard marshals events
onto the EDT, coalesces waveform blocks, and filters Alert History on `isAlert()`.
CSV failure is separately reported and does not hide the displayed event. No
delivery defect was found or invented; real Swing tests retain those assertions.

## Candidate implementation and development choice

- Remove warmup as a trigger veto; lower-quantile recent RMS resists transient
  contamination. The existing gain/threshold controls remain onset hints.
- A bounded hybrid analyzes startup and sustained sounds without requiring an
  energy jump. Periodic stride 1.25 s, onset deadline 2.5 s, minimum spacing .5 s;
  a pending onset gets its full tail, not an earlier silence-dominated window.
  One extra PCM block retains exactly aligned samples across a straddled read.
  EOF flushes short clips/tails; Stop discards them. Windows under RMS .0001 skip
  inference. Each same-class supported episode gets one alert until 5 s without
  support; suppressed repeated decisions remain visible and persist in CSV.
- Normalize only the selected feature window to RMS .1 in the **shared** extractor.
  Stored recordings, audio decoding and event input levels are unchanged. This
  removes one large gain dependence but cannot restore information lost to clipping.
- Retain JDK-only, class-balanced k=7. Promote the **existing** 47 licensed research
  recordings (42 hard-negative backgrounds, four explosions, one siren) into the
  bundled training ZIP, without moving/deleting/changing any original asset.
  Counts become 40/22/40/33/82. Development direct accuracy improves 64/99 → 73/99.
  Existing user training directories are deliberately not merged or overwritten;
  use a fresh `--data` root for the evaluated 217-file bundle. Custom/old roots
  have the same repaired algorithms but not this model's validation evidence.
- k-NN returns actual nearest distance for the chosen class and for background.
  AlertPolicy rejects an excessive chosen-class radius or poor separation from
  known backgrounds. Raw votes stay in CSV/UI and are **not used as probabilities
  or alert thresholds**. Radius / class-to-background distance-ratio cutoffs:
  scream 1.5/.8, explosion .3/.7, impact 1/.8, siren .75/.7, inclusive boundaries.
  These are development-selected selective operating points, **not probability
  calibration or an emergency-risk score**. No probability-calibration claim is made.
- New distance/conflict/duplicate statuses preserve every old CSV column and old
  status parsing. Diagnostics count onsets, analyzed/quiet windows and outcomes;
  UI/CLI now explain the live scheduler instead of asking users to stay quiet.

Feature-level prototypes on 99 development recordings compared original/normalized
features, original/expanded training and k=1/3/5/7/11/15. Normalized+217/k7 gave
73 direct matches versus 64 for baseline. A trim-and-repeat-active-audio prototype
was rejected (59/99 at k7, original training); it is not training augmentation or
shipped code. A permissive hybrid with the old vote gates gave **22 raw background
alerts instead of 2**; that configuration was rejected before final inference.
The conservative geometry gate trades some formerly easy quiet-prelude recall
for much fewer false alerts. Zero-background alternatives frequently reject
nearly everything, particularly screams/explosions (`acoustic-calibration.txt`).

The 99-file aligned development comparison (61 target, 38 background files) is:

| Scenario | Routed target intervals baseline → candidate | Background alerts baseline → candidate |
|---|---:|---:|
| Direct (not streaming) | 35 → 15 | 9 → 0 |
| Raw startup | 7 → 24 | 2 → 1 |
| Quiet prelude | 39 → 23 | 24 → 2 |
| Quiet, .25x gain | 35 → 26 | 10 → 2 |
| Quiet, 2x/clipping | 40 → 20 | 25 → 1 |
| Real-background bed | 21 → 12 | 11 → 0 |
| Continuous file intervals | 22 → 8 | 9 → 2 |
| Continuous background gaps | — | 9 → 5 |

Raw alert classification precision is 16/18 → 26/27; raw background exposure is
416.27 s, giving 17.30 → 8.65 false alerts per replay hour. Continuous exposure,
including gaps, is 713.27 s; counts fall 18 → 7, at a substantial recall cost.
There is **no across-scenario recall dominance**, and explosive-event alerting
is still poor. The point is an explicit safety-conscious tradeoff, not a claim
that making a detector more sensitive solves recognition. Final comparison must
decide whether to retain this candidate, not assume that passing tests is enough.

## Reproduction boundary

`ACOUSTIC_PROTOCOL.md` defines splits and weak-label metrics. Use baseline classes
from a detached `48da665` worktree, original `datasets/training`, and the current
`scripts/ReplayEvaluation.java`. Candidate uses the automatically installed fresh
217-file bundle. `AcousticStudy` only reads `rejection-dev`, never final data;
`calibrate_acoustic.py` refuses final file paths. The 42-file `acoustic-final`
remains unclassified at this freeze, with no new explosion/siren coverage.

`acoustic-candidate-dev.tsv` retains per-window predictions and distances. Other
scenario matrices, precision/recall, abstentions, exposure and latency are in its
summary; all historical experiment files are untouched. The final section below
will record both improvements and remaining failures without retuning.

## Fresh-final outcome — candidate retained as a limited repair

Frozen runtime/model/operating points: **`c1f0200`**, before either final replay.
All 42 source/uploader-disjoint recordings were processed once by each runtime;
no final outcomes were used to change features, training, gates or scheduling.
The final is now inspected and cannot be an untouched test for later work.

| Metric | Baseline | Frozen candidate |
|---|---:|---:|
| Direct labels correct | 23/42 (54.76%) | 25/42 (59.52%) |
| Direct target labels correct (no background) | 12/18 | 11/18 |
| Direct background recall | 11/24 | 14/24 |
| Direct correctly routed targets | 9/18 | 5/18 |
| Direct background alerts | 1/24 | 0/24 |
| Raw correctly routed target intervals | 2/18 | 7/18 |
| Raw alert precision | 6/10 (60%) | 7/8 (87.5%) |
| Raw background false alerts / exposure | 4 / 298.59 s | 1 / 298.59 s |
| Raw background false alerts/hour | 48.23 | 12.06 |
| Raw no classification emitted | 35/42 | 0/42 |
| Raw insertion-to-alert median / max | 5.25 / 7.1 s | 2.44 / 8.2 s |

Direct class precision/recall: scream 3/5, 3/8 → 3/4, 3/8; impact 9/11,
9/10 → 8/9, 8/10; background 11/12, 11/24 → 14/16, 14/24. Raw routed
recall is scream 0/8 → 1/8 and impact 2/10 → 6/10. New-final explosion and
siren recall remain **unmeasured**, not zero or perfect.

| Replay scenario | Routed targets baseline → candidate (/18) | Background alerts baseline → candidate | Negative exposure | False alerts/hour baseline → candidate |
|---|---:|---:|---:|---:|
| Quiet | 15 → 6 | 15 → 0 | 424.59 s | 127.18 → 0 |
| Quiet, .25x gain | 12 → 7 | 5 → 0 | 424.59 s | 42.39 → 0 |
| Quiet, 2x/clipping | 15 → 6 | 19 → 0 | 424.59 s | 161.10 → 0 |
| Real-background bed | 9 → 2 | 4 → 3 | 424.59 s | 33.92 → 25.44 |
| Continuous, including gaps | 9 → 2 | 11 → 2 | 424.59 s | 93.26 → 16.96 |

Continuous combined alert precision is 14/25 → 2/4; correct-alert median/max
latency is 1.9/7.1 → 5.9/8.2 s. No classifications in continuous file intervals
falls 13 → 4, but that is **not** improved alert recall. Zero observed false
alerts in short quiet scenarios does not establish zero risk.
Isolated continuous-gap false alerts actually increase 1 → 2; the combined
continuous count improves because alerts within background files fall 10 → 0.

**Decision:** retain the limited repair on the fix branch because it corrects
startup/alignment defects, improves overall direct background specificity and raw
routing, and reduces false-alert counts in each complete replay scenario (not
every subinterval, as the gap regression shows). Explicitly
accept the reduced quiet/continuous recall only as the documented conservative
operating tradeoff, **not** as fulfillment of reliable recognition. Target-only
direct identification did not improve; direct scream/siren confusion remains.
The requested reliable-recognition definition of done is therefore **not met**.
Do not deploy this as an emergency detector. Keep the best measured variant
rather than promoting the rejected sensitive flood configuration; the concrete
replacement path is [ACOUSTIC_REPLACEMENT.md](ACOUSTIC_REPLACEMENT.md).

`acoustic-{baseline,candidate}-final.{tsv,txt}` preserves all actual predictions,
statuses, matrices, precision/recall, abstentions, latency and false-alert counts.
`acoustic-final-failures.tsv` explains each file's direct/raw result from those
rows: no emitted window, no correctly labeled window, or a named gate rejection.
These are observed stage outcomes, not invented acoustic-onset annotations.
