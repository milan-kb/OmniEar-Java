# Acoustic repair protocol — 2026-10-15

Baseline: `48da665`, Java 21, unchanged 170-recording model. Baseline test/demo
and 99-file `rejection-dev` replay executed before any runtime modification.
The 261 passing checks reproduce the ten demo files' five direct matches and
zero raw events. Those selected examples are regressions, not accuracy estimates.

## Data boundary

All existing held-out/final sets have already been inspected in earlier work.
Use the 99-file `rejection-dev` pool for development; the previous 48-file
`rejection-final` is a historical regression comparison, not an unbiased final.
Training uses only licensed `training/` and, if development justifies promotion,
the existing 47 `additional-training/` recordings. Do not train on development.

`scripts/acoustic_selection.py` freezes 42 new recordings using human labels,
descriptions, tags, licenses and uploader/source exclusion, not predictions:
8 human screams, 10 glass/ceramic breakages, 24 varied backgrounds, each from a
distinct uploader absent from all 364 existing recordings. No workshop screams,
known composites, generated sounds or synthetic relabeling. Full recordings,
no crops or gain processing of assets. The existing checked acquisition workflow
records source/WAV/decoded hashes and attribution. Metadata curation is not an
independent listening-panel validation; unknown remixes cannot be excluded.

**There are no confidently documented unused traffic-siren or physical-explosion
sources in this selection.** Do not fill the deficit with alarm synthesis,
fireworks, voice effects or ambiguous booms. New-final performance for these two
classes is unmeasured; historical sirens/explosions remain regression-only.

Keep `acoustic-final` sealed from inference until the candidate code, training
and operating points are committed. Compare baseline and frozen candidate once;
do not retune on final outcomes. Preserve reports even if a candidate is rejected.

## Acceptance and reporting

Measure direct accuracy and per-class precision/recall independently of gates;
streaming correctly routed target-interval recall, first labels, abstentions,
alert precision, background alert counts/exposure and false alerts/hour, plus
file-insertion-to-correct-alert latency. Reuse all raw, quiet, .25x/2x clipped,
background-bed and continuous scenarios from `ReplayEvaluation.java`. These are
digital transformations, not new independent recordings or a deployment rate.

The candidate should improve identification and raw routed recall with no raw
background-alert increase on development and final; examine every other scenario,
not only the favorable one. Any exception must be explained, not hidden by a
single aggregate. If this is unattainable, retain the best validated runtime
and document the remaining failure and a concrete Java-compatible replacement.
Do not claim emergency reliability, calibrated probabilities, acoustic-onset
annotations or physical microphone recognition from replay.

Required checks: `java Build.java test`, `ui-test`, `jar`, portable CLI
classify/analyze and restart, continuous replay, startup/short-event/sustained/
silence/gain/clipping/duplicate regressions, DSP numerical checks and microphone
input diagnostic if available. Preserve CSV columns and historical evidence.
