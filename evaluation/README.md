# Reproduce the detection experiments

## Current: acoustic repair (2026-10-16)

See [ACOUSTIC_PROTOCOL.md](ACOUSTIC_PROTOCOL.md),
[ACOUSTIC_DECISION.md](ACOUSTIC_DECISION.md) and
[ACOUSTIC_REPLACEMENT.md](ACOUSTIC_REPLACEMENT.md). Fresh-final identification
improves 23/42 → 25/42 and raw routed recall 2/18 → 7/18, but continuous routed
recall falls 9/18 → 2/18; this is not reliable emergency recognition.

The candidate was frozen at `c1f0200` before fresh-final inference. Results are
committed under `acoustic-{baseline,candidate}-{dev,final}.{tsv,txt}`. All test
sets are now inspected; reproducing a final is not permission to tune on it.

```sh
java Build.java test
java Build.java jar
python3 scripts/prepare_data.py --verify-only
python3 scripts/test_replay_metrics.py
# Installs 217 training files only if this data directory's training root is absent:
java -jar build/OmniEar.jar --classify datasets/held-out/siren_traffic/fsd-238228.wav --data build/acoustic-reproduce
java -Xmx512m -cp build/classes scripts/ReplayEvaluation.java build/acoustic-reproduce/training rejection-dev > build/acoustic-dev.tsv
python3 scripts/summarize_replay.py build/acoustic-dev.tsv
java -Xmx512m -cp build/classes scripts/ReplayEvaluation.java build/acoustic-reproduce/training acoustic-final > build/acoustic-final.tsv
python3 scripts/summarize_replay.py build/acoustic-final.tsv
```

For the paired baseline, build `48da665` in a separate detached worktree and
execute **the current** `scripts/ReplayEvaluation.java` from this repository,
with that worktree's `build/classes` and this repository's `datasets/training`.
Do not use current normalized features or the 217-file model as baseline.
`AcousticStudy.java` emits additional geometric evidence only on development;
`calibrate_acoustic.py` refuses final paths. Preserve the rejected permissive
candidate's development TSV rather than substituting it for the frozen runtime.

The older instructions below describe historical commits. Current runtime
predictions intentionally differ; use the stated historical runtime worktree
to reproduce an older study, not today's `build/classes`.

## Historical: false-alert rejection study

The background-conflict candidate at `4e4e17f` was frozen before the 48-file
`rejection-final` set was evaluated. It produced identical final predictions
to baseline `747f7e8` and was reverted at `d9befdd`. See
[REJECTION_DECISION.md](REJECTION_DECISION.md) and
[REJECTION_PROTOCOL.md](REJECTION_PROTOCOL.md). All test sets are now inspected.
`rejection-dev` pools the original 50 holdout, eight development backgrounds and
41 previously inspected final recordings (99 total). No fresh-final recordings
are used by the diagnostic/selection or supplementary hard-negative scripts.

From the current repository, reproduce baseline and development diagnostics:

```sh
java Build.java build
python3 scripts/prepare_data.py --verify-only
python3 scripts/test_replay_metrics.py
python3 scripts/check_rejection_evidence.py
javac --release 21 -cp build/classes -d build/study scripts/ReplayEvaluation.java scripts/RejectionStudy.java scripts/HardNegativeStudy.java
java -Xmx512m -cp build/classes:build/study RejectionStudy > development-probe.tsv
python3 scripts/summarize_replay.py development-probe.tsv
# This reads the committed development probe, never the final set:
python3 scripts/select_rejection.py
java -Xmx512m -cp build/classes:build/study HardNegativeStudy > hard-negative.tsv
python3 scripts/summarize_replay.py hard-negative.tsv
java -Xmx512m -cp build/classes:build/study ReplayEvaluation datasets/training rejection-final > baseline-final.tsv
python3 scripts/summarize_replay.py baseline-final.tsv
```

Reproduce the **rejected** candidate using a detached worktree:

```sh
git worktree add --detach /tmp/opencode/omniear-conflict-candidate 4e4e17f
(cd /tmp/opencode/omniear-conflict-candidate && java Build.java test)
# Execute from this repository; training and evaluation files are the frozen selections.
java -Xmx512m -cp /tmp/opencode/omniear-conflict-candidate/build/classes scripts/ReplayEvaluation.java datasets/training rejection-final > candidate-final.tsv
python3 scripts/summarize_replay.py candidate-final.tsv
diff -u baseline-final.tsv candidate-final.tsv
```

The diff is empty. Substitute `rejection-dev` for the split to reproduce the
candidate's development comparison. On Windows use `;` in compiled classpaths.
The candidate's geometry/regression tests live at its frozen commit. The retained
runtime suite is unchanged and passes 217 checks. New audio can be reacquired by
`python3 scripts/prepare_data.py`; `rejection_selection.py` preserves the original
metadata-only selection process and does not choose new IDs from predictions.

## Earlier: periodic-window experiment

**Outcome: candidate rejected and reverted. Current main uses the original
170-recording model and RMS-gated runtime.** The 47 additional training files
are research-only. Application and evaluation inference use only Java 21. Python standard library
summarizes TSV; Python/ffmpeg are needed only to reacquire licensed data.

```sh
java Build.java build
python3 scripts/prepare_data.py --verify-only
python3 scripts/test_replay_metrics.py
java -Xmx512m -cp build/classes scripts/ReplayEvaluation.java datasets/training development > development.tsv
python3 scripts/summarize_replay.py development.tsv

# Current accepted runtime, not the rejected candidate. The set is now inspected.
java -Xmx512m -cp build/classes scripts/ReplayEvaluation.java datasets/training final-test > final.tsv
python3 scripts/summarize_replay.py final.tsv
```

To reproduce the original paired baseline, create a detached worktree at
`5a5b764`, run `java Build.java build` **inside that worktree**, then run the
current `scripts/ReplayEvaluation.java` from this repository with the baseline
`build/classes` classpath and baseline `datasets/training` absolute path.
This uses the original 170-file model, extractor, gate and capture logic on
exactly the same new/development recordings and mixtures. No code is cherry-picked
into the baseline. The original 50-file historical reports remain unchanged.

To reproduce the **rejected** candidate, build a detached worktree at `241bed3`
(frozen before final inference). Its `datasets/training/` contains the 217 files
used in the experiment; its extractor has the explicit window-transform constructor.
Run this repository's `ReplayEvaluation.java` with that worktree's `build/classes`
classpath and training path. For `WindowDevelopment.java`, run from the candidate
worktree using this script's absolute path, arguments `16000 true`, and the
candidate classpath; pipe its TSV to `select_stream_gates.py`. The `199361d`
worktree reproduces the data-only ablation. Do not train on the final files.

Reports named `stream-final.*` describe the **rejected candidate**, not current
main. `stream-baseline-final.*` describes the retained runtime. The code and
dataset snapshots remain in git history; the rollback did not erase evidence.

## Meaning of the metrics

- `DIRECT` is one loudest-window decision for the original file. Its classification
  matrix is independent of alert rejection. No continuous-time rate/latency is
  assigned to direct inspection.
- Raw replay feeds original recordings as 100 ms PCM blocks with a fresh pipeline.
  Quiet adds 3 s digital silence. Low/high use the same quiet prelude and gains
  0.25/2 (high clips to [-1,1]). These are transformations, not new recordings.
- Bed adds real same-split background, looped to cover the clip and its 3 s
  prelude, scaled to RMS 0.015; target retains original gain; the mixture clips
  to [-1,1]. The deterministic background index is file index modulo background
  count. This is a controlled digital mixture, not a physical SNR/room test.
- Multi uses one uninterrupted pipeline, inserting every file once with 3 s
  real-background gaps. `multi` and `multi-gap` describe intervals of **the same
  stream**, not independent runs. Background exposure in file intervals and gaps
  is summed when reporting the combined stream rate.
- Each target file is one weakly labeled interval. Recall means at least one
  correctly routed alert in that interval; repeated instances inside a file are
  not separately annotated. First-decision and first-alert matrices include an
  abstention column. Background labels can be wrong even when alerts are rejected.
- Alert classification precision counts correctly labeled alerts in target
  intervals divided by all routed alerts. Prelude alerts are false, even if their
  labels happen to match the later target. Background false alerts/hour counts
  all alerts in negative files and target preludes, divided by their actual audio
  duration (including digital silence where applicable). Onset/offset ambiguity
  at multi boundaries makes this conservative: late target tails in a gap count
  as false alerts. No deployed false-alarm rate is inferred from these few minutes.
- Latency is emission time minus **file insertion** time for correctly detected
  target intervals. Uploader clips have no independent acoustic-onset annotation;
  slow approach/initial quiet can make this proxy long. Misses have no measured
  latency; recall must be read alongside the latency statistics.
- In the rejected periodic candidate only, same-class supported windows merge
  into an episode until 3 s without support. The baseline and conflict candidate
  use the original energy-gated capture schedule without this episode policy.
  TSV preserves every non-silent decision, including rejected/repeated outcomes
  through its `alert` flag. The application CSV additionally stores the rejection
  status; TSV classifications/votes/distances are actual model outputs.

The 41 final files contain only two explosion recordings and three sirens. They
were uploader/known-source-disjoint and frozen before inference; they are still
a small metadata-curated sample, not an emergency-response validation corpus.
