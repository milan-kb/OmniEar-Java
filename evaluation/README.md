# Reproduce the continuous-stream experiment

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
- Same-class supported windows merge into an episode until 3 s without support.
  TSV preserves every non-silent decision, including rejected/repeated outcomes
  through its `alert` flag. The application CSV additionally stores the rejection
  status; TSV classifications/votes/distances are actual model outputs.

The 41 final files contain only two explosion recordings and three sirens. They
were uploader/known-source-disjoint and frozen before inference; they are still
a small metadata-curated sample, not an emergency-response validation corpus.
