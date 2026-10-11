# Candidate rejected; original runtime retained

Data selection froze at `199361d`; periodic candidate froze at `241bed3` before
final inference. `ac6a22b` reverted the candidate after it failed the no-increase
in background false-alert requirement. There was no threshold tuning on final
recordings. Further development requires a new untouched final set.

## Diagnosis and comparison

The original 20-block warmup swallows short startup events. Its event-contaminated
rolling mean prevents sustained sirens from triggering; the 6×/0.02 gate misses
quiet sounds. The 1.3 s post-trigger capture introduces quiet context unlike the
loudest 2.5 s training window. Vote-only gates accept distant neighbors.

On development (the old 50 files plus eight new backgrounds), compared adaptive
onset-only (2× rise over an EMA, floor 0.002), 2 Hz periodic one-second windows,
and hybrid onset neighborhoods plus a 1 Hz fallback. With development-selected
vote/distance gates, they detected respectively 82, 108 and 107 of 200 repeated
target/scenario cases. These are 40 targets repeated under five scenarios, not
200 independent recordings. The final candidate used normalized one-second
windows for both training/inference, original balanced k=7, evidence rejection
and same-class episode deduplication. No synthetic training recordings were used.

Full-runtime development raw recall improved 4/40 → 22/40, with zero background
alerts for both; quiet-prelude background alerts fell 10 → 0. The candidate's
conservative gate detected no development explosions. Experiments with 2.5 s
windows and square-root class balancing were rejected during development.

## Final quality gate

On 41 new source-independent recordings, raw target recall rose 3/21 → 9/21,
but background false alerts rose **2 → 5**, and continuous-stream false alerts
rose **9 → 10** including inter-file background. Direct accuracy fell 24/41 →
22/41. The periodic candidate therefore was not promoted despite development
gains. Full matrices/predictions are retained in the stream reports.

The frozen data-only ablation (original runtime with 217 training files) was also
rejected on **development**, without another final-set search: raw background
alerts rose 0 → 1 and real-background-bed alerts rose 0 → 3. Its 47 additional
assets are retained separately for future research; the default model uses the
original 170 files. No runtime improvement is claimed from this work.
