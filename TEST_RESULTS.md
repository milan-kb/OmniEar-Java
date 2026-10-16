# Verification results

## Acoustic repair — 2026-10-16

Branch: `fix/acoustic-detection`; baseline `48da665`; new data freeze `102e86f`;
runtime/model/operating-point freeze **`c1f0200`**, before fresh-final inference.
The runtime is retained as a limited startup/specificity repair, **not a reliable
recognition solution**. No final-set retuning occurred. Earlier sections and all
original assets/results remain historical evidence, not today's expected output.

### Reproduced failure and implemented repair

Before editing, `java Build.java test` passed 261 checks and the real-display
Swing smoke passed, yet all ten actual raw demo replays emitted **zero events**.
`evaluation/acoustic-baseline-demo.tsv` traces startup above-threshold blocks
ignored during warmup, foreground-contaminated mean baseline, both sustained
sirens below the sixfold threshold, and direct classifier errors independently
of Swing. The old 1.3 s tail/3.5 s ring was misaligned with 2.5 s training windows.

The repair uses noise-resistant onset hints without a warmup veto, bounded
periodic/onset-tail windows, exact aligned samples, EOF flushing, silence skip,
shared RMS-.1 feature normalization, 217 existing licensed training recordings,
development-selected class-radius/background-separation rejection and 5 s
same-class episode suppression. Original vote shares remain diagnostics, **not
probabilities**; CSV columns and old statuses remain readable. Rejections and
duplicates remain visible/persisted. UI and CLI explain startup analysis and
report bounded outcome counters. No new runtime dependency was added.

Independent DFT/MFCC-reference tests find no FFT/MFCC formula defect. Sparse
source diversity, level/ambience dependence, temporal pooling and overlapping
class geometry still limit recognition. Full per-demo causes and per-final-file
stage outcomes: [decision](evaluation/ACOUSTIC_DECISION.md),
[`acoustic-final-failures.tsv`](evaluation/acoustic-final-failures.tsv).

### Actual before/after evidence

Development uses 99 already-inspected recordings (61 targets, 38 backgrounds).
Fresh final uses 42 metadata-selected, distinct-uploader recordings disjoint from
all existing sources: 8 screams, 10 glass/ceramic breaks, 24 backgrounds. No unused
documented explosion or traffic-siren positive was available; their independent
final recall is **unmeasured**. Weak file labels give insertion-to-alert latency
proxies and file-interval recall, not annotated onset or event-instance recall.

| Metric | Baseline | Candidate |
|---|---:|---:|
| Development direct labels | 64/99 | 73/99 |
| Development raw correctly routed targets | 7/61 | 24/61 |
| Development raw false alerts/hour (416.27 s negative exposure) | 17.30 (2 alerts) | 8.65 (1 alert) |
| Fresh-final direct labels | 23/42 | 25/42 |
| Fresh-final **target-only** direct labels | 12/18 | 11/18 |
| Fresh-final direct routed targets | 9/18 | 5/18 |
| Fresh-final raw correctly routed targets | 2/18 | 7/18 |
| Fresh-final raw alert precision | 6/10 | 7/8 |
| Fresh-final raw false alerts/hour (298.59 s negative exposure) | 48.23 (4 alerts) | 12.06 (1 alert) |
| Fresh-final raw no classification | 35/42 | 0/42 |
| Fresh-final raw latency median / max | 5.25 / 7.1 s | 2.44 / 8.2 s |
| Fresh-final continuous routed targets | 9/18 | 2/18 |
| Fresh-final continuous false alerts including gaps (424.59 s exposure) | 11 | 2 |

Fresh-final scenario routed recall: quiet 15/18 → 6/18; .25x gain 12/18 → 7/18;
2x/clipping 15/18 → 6/18; real-background bed 9/18 → 2/18. False alerts respectively
15 → 0, 5 → 0, 19 → 0, 4 → 3. Continuous **gap-only** false alerts increase 1 → 2,
while alerts in background-file intervals fall 10 → 0. Scream direct recall stays
3/8 and five direct screams still become sirens; impact direct recall falls
9/10 → 8/10. Raw routed recall improves scream 0/8 → 1/8, impact 2/10 → 6/10.
This is an explicit conservative tradeoff, not across-scenario recall success.
The permissive hybrid was rejected after **22** development raw background alerts
(baseline 2); its evidence is preserved, not selectively omitted.

Full confusion matrices, per-class precision/recall, abstentions, individual
decisions, other latency statistics and exposures are preserved in
`evaluation/acoustic-{baseline,candidate}-{dev,final}.{tsv,txt}`. A zero numerator
over a few minutes is not an established zero false-alarm rate. Final recordings
are now inspected and must not be reused as an untouched evaluation.

### Checks and hardware scope

- `java Build.java test`: **560 checks pass** (baseline 261). Existing mechanics
  assertions were updated only where behavior intentionally changed; no skipped
  tests or suppressed compiler warnings. New regression tests fail on old gain,
  startup and read-straddling behavior; an inverted radius-gate mutation also
  fails. Numerical references, silence, bounded calls, deduplication, geometry
  rejection and unchanged-column CSV round-trips pass.
- `java Build.java ui-test`: **pass**, real displayed Swing widgets, keyboard
  navigation, recovery and persistence. Packaged `TrainedSmokeTest --ui`: **pass**,
  217-file fresh model, real scream classification, actual wind-label mismatch
  visibly suppressed, minimum-size controls and CSV close/reopen. No focus
  assertion was removed or skipped.
- `java Build.java jar`, JAR help, packaged `omniear.TestSuite` (**560 checks**),
  packaged/portable `SubmissionDemoTest`, fresh CLI startup/restart, WAV inference
  and continuous WAV analysis: **pass**. Real sustained siren 238228 routes one
  P4 alert plus explicit duplicate/rejection outcomes through MonitorService/CSV.
  Historical demo direct labels improve 5/10 → 7/10, **not** an accuracy estimate.
- Data audit: **406** licensed unique recordings, original **364** manifest
  records and WAVs unchanged; fresh bundle has exactly 217 training WAVs with
  no held-out/final audio. All ten separate demo assets and attributions remain.
  Existing training is preserved, not auto-upgraded; use a new `--data` directory
  for the evaluated model. `jdeps -s build/OmniEar.jar`: only `java.base`, `java.desktop`.
- Development replay `accept()` DSP/scheduler/kNN p95 **21.874 ms**, max **26.745 ms**
  per ≤100 ms PCM block; kNN mean .031 ms over 4,026 calls. Final replay max
  **25.561 ms**, kNN mean .024 ms. These host-specific timings exclude CSV/UI/EOF;
  they do not establish real-time guarantees or behavior with 5,000 custom files.
- OS-default input five-second `--mic-test`: **capture/release works**, RMS .6824
  (-3.3 dBFS), peak 1, .41% samples near full scale; reports **possible clipping**.
  No controlled labelled physical-microphone recognition was validated; selected
  input could be a loopback. Do not call this a successful emergency test. See
  [`acoustic-microphone.txt`](evaluation/acoustic-microphone.txt).

Recorded command outputs: [`acoustic-verification.txt`](evaluation/acoustic-verification.txt).
Linux/JDK 21 only; Windows/macOS, long-duration microphone behavior and assistive
technology remain untested. The reliable-identification definition of done is
**not met**. Keep this limited repair and do not deploy it as an emergency system.
A concrete pretrained, Java-compatible replacement path with dependency,
frontend, calibration and validation requirements is documented in
[ACOUSTIC_REPLACEMENT.md](evaluation/ACOUSTIC_REPLACEMENT.md); it is not installed
or claimed validated here. Exact replay commands are in [evaluation/README.md](evaluation/README.md).

## Conservative code cleanup — 2026-10-14

Baseline: `86d850a`; refactor branch: `refactor/code-cleanup`.

- Six Swing component searches now share the test-only typed, depth-first
  traversal in `test/omniear/SwingTestSupport.java`. Child order, first-match
  selection and the table-root case are preserved at every existing caller.
- Three EDT readiness loops share one polling method. Predicates still execute
  through `invokeAndWait`; callers retain their 15 s / 20 ms or 30 s / 25 ms
  timeout/interval and original failure assertions. CSV-release polling is unchanged.
- The k-NN mean array is constructor-local rather than retained model state.
  All calculations and their order are unchanged; no training-data changes or tuning.
- `java Build.java test` passes **261 checks**: all 249 original checks plus twelve
  traversal/polling regressions. The packaged `omniear.TestSuite` also passes 261.
  Separate temporary mutations inverted the search predicate and readiness
  condition; both were rejected by the new tests without modifying repository files.
- `java Build.java ui-test`, packaged `omniear.UiSmokeTest` and packaged
  `omniear.TrainedSmokeTest --ui` pass on isolated rootful Xwayland, including
  keyboard focus/navigation, genuine-demo inference, CSV reopen and cleanup.
  No assertion was skipped; the primary-desktop focus limitation remains.
- `java Build.java jar`, JAR help and `omniear.SubmissionDemoTest` pass. Demo TSV
  and help match the baseline byte-for-byte, including demo execution outside the
  repository. The 170 training WAVs and ten separate demo resources are unchanged;
  `jdeps` still reports only `java.base` and `java.desktop`.
- `scripts/ReplayEvaluation.java datasets/training rejection-dev` and
  `rejection-final` produce byte-identical baseline/refactor TSVs: 1,370 and 671
  non-header rows respectively. This is behavior comparison, not new ML evidence.
- Fresh portable CLI launch/restart, existing-training preservation and gated WAV
  analysis pass. CLI output and persisted CSV match after normalizing only UUIDs
  and wall-clock timestamps. Temporary data is cleaned up; acoustic limitations
  and known demo failures are unchanged.

## Academic submission and demonstration — 2026-10-12

The submission adds a guided, licensed WAV demo, explicit model/mode indicators,
read-only microphone gain diagnostics and academic documentation. The classifier,
FFT/MFCC features, detector, capture pipeline, alert gates, PCM conversion and
accepted training recordings are unchanged from `fb5246f`.

- **249 regression checks pass**, including six input-level boundary checks and
  26 checks for the ten actual demo recordings and separate training resources.
  A temporary mutation raising the near-full-scale cutoff from 0.99 to 2.0 was
  rejected by the diagnostic test; the original source was restored afterward.
- `demo/VERIFIED_RESULTS.tsv` records current inference: one selected direct
  success and one known failure per category; all ten raw replays emit no event.
  This is a teaching selection, not a representative accuracy estimate.
- Swing smoke tests passed with real displayed widgets, keyboard shortcuts,
  invalid-WAV recovery, CSV persistence/reopen, untrained prediction controls and
  explicit memory-only status. The genuine-data test clicked **Classify example**
  and displayed the actual wind → scream/P0 mismatch. Renders were inspected,
  including the minimum window size; provenance/results panes scroll and resize.
- KDE/Wayland intermittently refused Robot focus on the primary desktop. Tests
  now allow native-event/placement delays and bounded focus attempts, retaining
  assertions. The complete suites passed on an isolated **rootful Xwayland**
  display using the system's existing server; no dependency was installed or
  desktop permission bypassed. Manual input focus still matters on the main desktop.
- The physical input diagnostic captured/released five seconds: RMS 0.0012
  (-58.2 dBFS), peak 0.0321, near-full-scale samples 0.00%. See
  `demo/MICROPHONE_CHECK.txt`. This is decoded input operation, not evidence of
  threat recognition or absence of analog clipping.
- Dataset audit passes all 364 recordings. The five paired rejection-study
  summaries still reproduce; historical experiment predictions are preserved.
- Fresh local clone of submission snapshot `85884be` (no existing build/data):
  `java Build.java test` passed 249 checks; JAR build/help passed; `jdeps` found
  only `java.base` and `java.desktop`. Both Swing suites also passed using the
  **packaged JAR classes** on the isolated display, including the primary demo
  button being fully visible at 1060×820.
- Portable JAR run from an empty temporary working directory reproduced the
  committed demo TSV exactly without repository files. Separate fresh CLI
  launch/restart installed 170 training WAVs, classified explosion 256296 as
  explosion/P0 twice, preserved two correctly marked CSV records, and completed
  gated WAV processing. ZIP inspection verified **170 training + 10 separate demo
  WAVs**; demo hashes, labels, authors, source URLs and licenses match the manifest.

For Linux test hosts with compositor focus restrictions and an existing Xwayland
installation, the equivalent isolated-display check can be run in two terminals
(choose an unused display number; stop the server after testing):

```sh
# Terminal 1 — optional system test facility, not an application dependency:
Xwayland :9 -geometry 1360x1000 -nolisten tcp -noreset
# Terminal 2 — same real Swing/Robot assertions, no skipped checks:
DISPLAY=:9 java Build.java ui-test
DISPLAY=:9 java -cp build/classes omniear.TrainedSmokeTest --ui
```

Submission documents: [report](docs/PROJECT_REPORT.md), [UML](docs/UML.md),
[five-minute demo/viva](docs/DEMO.md), [feature audit](docs/FEATURE_AUDIT.md).
No new ML experiment, prediction fabrication or reliability claim was introduced.

**Previous experiment (2026-10-12): the background-conflict candidate was rejected and
reverted because it reduced no final-test false alerts.** The original runtime
and 170-recording model are retained. Full diagnosis, development comparisons
and reproduction: [evaluation/REJECTION_DECISION.md](evaluation/REJECTION_DECISION.md).

## False-alert investigation — 2026-10-12

Baseline `747f7e8`; data freeze `9ac2ab0`; candidate freeze `4e4e17f`;
rollback `d9befdd`. No periodic/onset/hybrid scheduler was reintroduced.
The candidate suppressed scream/impact alerts when a background training
neighbor was sufficiently closer, retaining the actual class/vote and an explicit
rejection status. On 99 development files, it preserved all 199 baseline-correct
recording/scenario intervals while reducing background alerts 99 → 94
(including direct decisions; streaming alone 90 → 87). These are repeated
scenarios, not 199 independent events.

### Fresh final comparison

48 source/uploader-disjoint recordings: 8 screams, 8 breakages, 2 traffic sirens,
30 backgrounds, **no explosions**. Selection and candidate were frozen before
final inference; no final-set retuning. Every one of the 671 TSV rows is identical
between baseline and candidate, including all predictions, alert flags and times.

| Metric | Baseline | Rejected candidate |
|---|---:|---:|
| Direct classification accuracy | 25/48 (52.08%) | 25/48 (52.08%) |
| Direct correctly routed target recall | 15/18 | 15/18 |
| Direct alert precision | 15/24 (62.50%) | 15/24 (62.50%) |
| Direct background clips passing gates | 8/30 | 8/30 |
| Raw routed target recall | 1/18 (5.56%) | 1/18 (5.56%) |
| Raw alert precision | 1/4 (25%) | 1/4 (25%) |
| Raw background false alerts / exposure | 3 / 329.96 s | 3 / 329.96 s |
| Raw false alerts/hour | 32.73 | 32.73 |
| Raw no emitted classification | 41/48 | 41/48 |
| Raw files with no routed alert | 44/48 | 44/48 |
| Raw correct-alert latency median / maximum | 4.3 / 4.3 s | 4.3 / 4.3 s |

All following results are also identical for baseline and candidate. Continuous
multi includes background gaps in its false-alert count, precision and exposure.

| Scenario | Routed targets (/18) | False alerts | False alerts/hour | Alert precision | No emitted classification (/48) | Correct-alert latency median / max (s) |
|---|---:|---:|---:|---:|---:|---:|
| Quiet prelude | 16 | 25 | 189.89 | 17/44 | 0 | 1.5 / 3.5 |
| Quiet, 0.25× gain | 11 | 13 | 98.74 | 11/24 | 13 | 1.7 / 3.3 |
| Quiet, 2× gain/clipping | 16 | 29 | 220.27 | 19/49 | 0 | 1.5 / 3.5 |
| Real-background bed/prelude | 9 | 18 | 136.72 | 10/31 | 16 | 1.7 / 2.6 |
| Continuous multi-file + gaps | 9 | 17 | 129.13 | 11/29 | 20 | 1.5 / 2.5 |

Negative exposure is 473.96 s per prelude scenario and for the combined stream.
Latency is file-insertion-to-emission for detected targets, **not acoustic-onset
latency**; misses are excluded from latency and included in recall. First-label
and first-alert confusion matrices, per-class precision/recall and per-window
predictions are in `evaluation/rejection-final-{baseline,candidate}.{txt,tsv}`.
Few minutes of transformed audio do not establish a deployed false-alarm rate.

### Verification of the retained release

- Clean Java 21 warning-free build and **217 passing checks**; startup/restart,
  WAV decoding, persistence, anomaly detection and alert routing included.
- Real-display Swing smoke test and genuine development-file UI classification
  passed: scream 264918 → scream/P0, vote 0.716, distance 0.981.
- Dataset audit passed all 364 assets; all 316 prior manifest entries/hashes
  unchanged. `src/`, `test/` and `Build.java` match `747f7e8` after rollback.
- Replay metric regression passed; paired summaries reproduce exactly from TSV.
- JAR build/help and portable fresh startup/restart passed; genuine explosion
  256296 → explosion/P0, vote 0.764, distance 0.478. Gated WAV processing completed.
  The JAR bundles exactly 170 training WAVs; `jdeps` shows only `java.base` and
  `java.desktop`. Rejected test classes were removed by a clean rebuild.
- Physical microphone diagnostic captured/released five seconds: RMS 0.0010,
  peak 0.0282, signal received. It validates input operation only, not room-event
  recognition; no raw microphone data was retained.
- Candidate mechanics passed 229 checks before rejection; its implementation
  and geometry/status persistence tests remain available at `4e4e17f`.

No measured final false-alert improvement was achieved. Missing explosion
coverage, two sirens, acted screams, breakage semantics and weak file-level
labels limit the conclusions. No emergency-response reliability is claimed.

The following sections are historical results for earlier experiments.

## Trained release — 2026-10-10

170 real, licensed FSD50K training recordings; 50 held-out recordings, **10 per
class**. Train/test share no known source/session groups or uploaders. All five
classes were evaluated; **zero skipped files**. Source counts and semantic
limitations are in [DATASETS.md](DATASETS.md). Full per-file predictions and all
matrices are committed in [evaluation/held-out.txt](evaluation/held-out.txt).

### Direct WAV classification (Stage 1 bypassed)

**36/50 correct: 72.00% accuracy.** Rows are actual labels; columns are predictions
in order: scream, explosion, impact/crash, traffic siren, background.

```text
                    scream explosion impact siren background
scream_distress         7       0       0      3       0
explosion               0       9       0      0       1
impact_crash            0       0      10      0       0
siren_traffic           0       0       0      6       4
background              1       5       0      0       4
```

| Class | Test n | Precision | Recall | False-positive predictions |
|---|---:|---:|---:|---:|
| Distress scream | 10 | 87.50% | 70.00% | 1 |
| Explosion | 10 | 64.29% | 90.00% | 5 |
| Impact/crash | 10 | 100.00% | 100.00% | 0 |
| Traffic siren | 10 | 66.67% | 60.00% | 3 |
| Background | 10 | 44.44% | 40.00% | 5 |

Background false-positive rate: **6/10 non-background labels**, of which **2/10
pass the existing direct-clip alert gates**. This small, selected dataset does
not establish generalization to arbitrary audio or emergency intent.

### Complete Stage 1 → Stage 2 replay

**Raw recordings**, unchanged amplitude/timing, default detector, fresh 2-second
calibration at each file's start. One row per file; first emitted event determines
the label; `NO_EVENT` is an abstention, not a background prediction.

```text
                    scream explosion impact siren background NO_EVENT
scream_distress         0       0       0      0       0        10
explosion               0       0       0      0       0        10
impact_crash            0       0       4      0       0         6
siren_traffic           0       0       0      0       0        10
background              0       1       0      0       1         8
```

- **5/50 = 10.00% first-event label accuracy**, counting abstentions as misses.
- Stage 1 triggered in 6/50 recordings: four impacts and two backgrounds.
  Ten events total, seven routed alerts, across **474.50 seconds** of input audio.
- Correct-category routed alerts: scream 0/10, explosion 0/10, impact 4/10,
  siren 0/10. **Background recordings with routed alerts: 0/10** (the wrong
  explosion label was suppressed by its vote gate).
- Per-class gated precision/recall/false positives are in the full report;
  absent predictions have undefined precision, shown as `N/A`, not 100%.

Many isolated clips end or contain the event during calibration. Sustained
sirens also raise the adaptive baseline rather than causing a sixfold onset.
These are important detector limitations, not missing Stage 2 training.

**Separate controlled diagnostic:** three seconds of **digital silence** before
each unchanged held-out WAV, with identical model/thresholds. First-event label
accuracy is **28/50 = 56%**; correctly routed alerts: scream 8/10, explosion 6/10,
impact 10/10, siren 0/10. Background alerts are **7/10**. This deliberately quiet
context exposes onset/capture-window distribution shift and false alerts.
It is **not additional real data, not training augmentation, and not live-event
detection evidence**. It must not replace the raw-recording results above.

### Development decisions and error investigation

Before viewing the test scores, ran leave-source-group-out validation exclusively
on the 170 training files, refitting scaling on each fold. See
[evaluation/development.txt](evaluation/development.txt) and
`java -cp build/classes scripts/DevelopmentCheck.java`.

| Votes / k | Training group-out accuracy | Macro recall |
|---|---:|---:|
| Original inverse-distance / 3 | 68.24% | 62.33% |
| Original inverse-distance / 7 | 72.35% | 64.61% |
| Class-balanced inverse-distance / 3 | 71.76% | 69.61% |
| Class-balanced inverse-distance / 5 | 76.47% | 75.08% |
| **Class-balanced inverse-distance / 7 (selected)** | **76.47%** | **75.33%** |

Balancing divides each neighbor vote by its class's **training** count. This
addresses uneven genuine data without synthetic oversampling. Selected k=7 by
macro recall; FFT/MFCC and training-only standardization stayed intact. A failing
majority-bias regression preceded the change; exact-match behavior remains tested.
The development script asserts its balanced predictions agree with runtime k-NN.

Observed held-out failure modes (no subsequent fitting or threshold tuning):

- Three screams resemble pitched sirens under pooled MFCCs, including the
  muffled pillow scream and a distant/home-video scream.
- Four sirens become background: weak siren mixed with road/room ambience.
- Five backgrounds become explosion (speech/laughter, bells/traffic, a handclap),
  and one wind gust becomes scream. The full feature model remains sensitive to
  transients, level, padding and context; vote share is not calibrated certainty.
- Explosion training has only **three groups**, mostly one uploader. High recall
  on ten test clips from four groups is not broad validation.
- The detector captures only about 1.3 s after an onset. A sustained sound's
  capture includes pre-onset quiet, unlike its full direct 2.5 s window. This
  explains some calibrated-replay siren→explosion errors. Changing window timing
  or thresholds now would require separate development and a new untouched test
  set; the final test was not used to optimize them.

### Deliverable checks

| Check | Result |
|---|---|
| Baseline before checkpoint | PASS: 208 checks; all existing UI/training edits preserved in `d47e085` |
| `java Build.java test` | PASS: **217 checks**, including real bundled-model installation, fresh CLI launch/relaunch, persisted deterministic held-out predictions |
| `java Build.java ui-test` | PASS on KDE/Wayland display: all four tabs, WAV/pipeline workflow, keyboard navigation, persistence and cleanup |
| `java -cp build/classes omniear.TrainedSmokeTest --ui` | PASS: real 170-recording model loads asynchronously; unseen FSD50K 264918 visibly predicts scream, vote **0.716**, distance **0.981**, P0 |
| `python scripts/prepare_data.py --verify-only` | PASS: 220 WAVs, hashes, unique decoded PCM, selection/inventory match, uploader/group separation |
| `java Build.java jar` | PASS; training audio and attribution packaged; no external runtime dependencies |
| Clean local Git clone at `3cff1e1` | PASS: 217 tests, JAR build and provenance audit without any download/preparation step |
| Portable JAR launched outside repository | PASS: auto-installed 170 recordings; unseen explosion 256296 predicts explosion, vote **0.764**, distance **0.478**, P0; gated WAV analysis also completes |
| Clean-clone JAR evaluation | PASS: **exact text match** with committed `evaluation/held-out.txt`, using automatically installed training |
| `jdeps -s build/OmniEar.jar` | Only `java.base` and `java.desktop` |
| Five-second microphone diagnostic | Captured and released input; RMS **0.6838**, peak **1.0000**; **clipping reported** |

The keyboard smoke test exposed KDE/Wayland focus-stealing prevention: requesting
focus alone can be denied. The test now raises its own window and clicks its
header before Robot key input; the original focus and Alt+2 assertions remain.
No failed assertions were removed or tests skipped.
As a review mutation, inverting the bundled-installation directory guard caused
the real-data startup assertion to fail. The correct guard was restored and all
217 checks passed again.

The microphone result establishes device access, not input identity, gain quality,
or live recognition. No private audio was saved. Lower input gain before live use.
The model is immediately usable for WAV classification; live emergency detection
is **not validated** and the measured missed-event/false-alert limitations remain.

## Historical checkpoint verification (before real-data training)

The following records the preserved earlier implementation; its statements about
absent training/evaluation describe that checkpoint, not the trained release above.

Verified on CachyOS Linux using installed OpenJDK `21.0.12.1+1`.
The original baseline passed 200 checks before this revision.

| Check | Result |
|---|---|
| Java compilation (`--release 21 -Xlint:all -Werror`) | PASS, no warnings |
| `java Build.java test` | PASS: 208 checks |
| `java Build.java ui-test` | PASS on the real graphical desktop; four workflow tabs |
| `java Build.java jar` | PASS; `build/OmniEar.jar` created |
| `java -jar build/OmniEar.jar --help` | PASS; executable entry point |
| Regression suite and GUI smoke test using packaged JAR classes | PASS: 208 checks and real-display smoke test |
| `jdeps -s build/OmniEar.jar` | Only `java.base` and `java.desktop` |
| Five-second default Java Sound input test | PASS: captured input, automatically stopped/released; clipping reported |
| Real ESC-50 WAV decoding/resampling/features and gated analysis | PASS; see DATASETS.md for provenance and scope |
| `git diff --check` | PASS |

The regression suite includes headless command-line training, explicit clip
classification, gated WAV processing, missing-model refusal, CSV persistence,
PCM8/16/24/32 and float32/64 decoding, 8/16/44.1/48/96 kHz input, anti-aliasing,
downmixing, malformed/truncated audio, DSP, k-NN, detector boundaries and cleanup.
New checks cover manually named duplicate/conflicting training recordings,
separate held-out evaluation, training-overlap exclusion and refusing to report
accuracy when no eligible test recording exists. Core RMS, FFT/MFCC, k-NN and
alert-gate algorithms were preserved. Duplicate training files previously counted
multiple times under manually chosen filenames; these now count once, and
cross-class conflicts stop loading rather than biasing the model.
The 44.1 kHz duration test exposed a float-precision rate-ratio error; converting
the ratio in double precision fixed it and the regression now passes.

A deliberate review mutation changed the strict energy comparison from `>` to
`>=`. The threshold-equality test rejected it. The original comparison was
restored and the full suite then passed. This is a test-effectiveness check,
**not a classification evaluation metric**.
During this revision, inverting the held-out training-overlap check also caused
the suite to fail (`no score without eligible test samples`); the correct check
was restored before the final passing run.

The real-display smoke test loaded temporary mechanical fixtures, analyzed a
WAV, classified an unseen mechanical clip, recovered from an invalid WAV,
verified a known-fixture P0 alert and exact-match warning, evaluated a separate
held-out folder, exercised Alt+2 navigation and verified visible event rows at
the 1060×820 minimum size. It closed the window and reopened four persisted
events after the CSV lock was released. Four inspected renders of the actual
Swing components are available at `build/ui-tab-0.png` through `ui-tab-3.png`.
All labels/metrics in those test renders are mechanics fixtures, not evidence
of acoustic accuracy. Desktop screen capture
was intentionally replaced with component painting: this host's Wayland
permission portal blocks unattended `Robot.createScreenCapture()`. The
application itself does not perform screen capture.
The keyboard smoke test initially timed out when desktop focus was elsewhere;
it now requests and verifies window activation before sending Alt+2. The
packaged-JAR GUI test passed after that correction.

The microphone diagnostic captured five seconds from the system's default Java
Sound input and returned RMS **0.6834**, peak **1.0000**, with **Clipping: lower
input gain**. No raw input or diagnostic events were saved. This verifies device
opening/reading/automatic release in this environment, not the identity of the
physical input, room calibration or semantic recognition. Check OS input gain
and selected device before a presentation.

The new UI uses the standard JDK Metal look-and-feel, a shared dark palette,
keyboard mnemonics, labeled controls, textual severities and scrollable content.
Cross-platform rendering, assistive-technology operation and long-duration
microphone stability have not been tested.

## Not verified / unresolved limits

- No real acoustic dataset was supplied or bundled. Synthetic signals in tests
  exercise algorithms and plumbing only; temporary training fixtures are deleted.
- No semantic classification accuracy, confidence calibration or real-world
  safety performance has been evaluated. Java k-NN does not reproduce YAMNet.
- Physical input quality, gain and room-dependent calibration need a manual
  laptop rehearsal. The regular regression/GUI suite does not open the microphone;
  `--mic-test` is the separate device diagnostic used above.
- GUI audio and CSV error handling are implemented; OS/audio-device behavior
  outside this Linux/JDK environment has not been manually verified.
# Continuous-detection experiment — 2026-10-11

**Outcome: no runtime improvement promoted.** The periodic candidate improved
development recall, but increased background false alerts on the new final set.
It was reverted, as required. The original trained Java 21/JDK-only app and its
170-recording default model remain operational. Additional data and reproducible
evaluation tools are retained; they do not silently change the default model.

## Unbiased final comparison (frozen before inference)

Baseline `5a5b764`; candidate `241bed3`; rollback `ac6a22b`. The final 41 files
contain 8 screams, 2 explosions, 8 glass breakages, 3 traffic sirens and 20
backgrounds, with uploader/known-source separation from all training/development.
No final predictions were used to select features, windows or thresholds. The
set is now inspected and cannot serve as an untouched test for future tuning.

| Metric on new final set | Original / retained | Rejected periodic candidate |
|---|---:|---:|
| Direct classification accuracy | 24/41 (58.54%) | 22/41 (53.66%) |
| Direct background label recall | 6/20 (30%) | 8/20 (40%) |
| Direct background clips passing alert gates | 6/20 | 0/20 |
| Raw replay first-label accuracy | 6/41 (14.63%) | 21/41 (51.22%) |
| Raw replay classifier abstentions | 33/41 | 0/41 |
| Raw correctly routed target-file recall | 3/21 (14.29%) | 9/21 (42.86%) |
| Raw alert classification precision | 9/11 (81.82%) | 10/15 (66.67%) |
| Raw background false alerts / exposure | 2 / 196.12 s | 5 / 196.12 s |
| Raw false alerts per replay hour | 36.71 | 91.78 |
| Correct raw alert latency, median / maximum | 7.3 / 7.9 s | 1.0 / 6.6 s |

Latency is **file-insertion-to-emission**, not independently annotated acoustic
onset latency; undetected targets are excluded from latency and included in
recall. Each weakly labeled file is one target interval, not an event-instance
annotation. Zero classifier abstentions does **not** mean zero missed alerts.

| Simulated scenario | Correctly routed targets, original → candidate (/21) | Background false alerts, original → candidate | False alerts/hour, original → candidate |
|---|---:|---:|---:|
| Original WAV, fresh stream | 3 → 9 | 2 → 5 | 36.71 → 91.78 |
| Three-second digital quiet prelude | 15 → 10 | 14 → 5 | 157.94 → 56.41 |
| Quiet prelude, 0.25× recording gain | 13 → 10 | 8 → 5 | 90.25 → 56.41 |
| Quiet prelude, 2× gain with clipping | 15 → 9 | 13 → 5 | 146.66 → 56.41 |
| Real-background bed and prelude | 5 → 4 | 8 → 7 | 90.25 → 78.97 |
| Continuous multi-file stream, including gaps | 6 → 7 | 9 → 10 | 101.53 → 112.81 |

Prelude/continuous negative exposure is 319.12 s per scenario; these are repeated,
digitally arranged recordings, not extra independent samples or deployed hours.
All background preludes are included in false-alert accounting, even when a
spurious prelude label matches the later target. Continuous gap boundaries count
late target-tail alerts as false, conservatively. The candidate routed **0/2
explosions and 0/3 sirens** correctly in raw replay. It failed the false-positive
bar despite selected improvements, so no emergency-response reliability is claimed.

## Diagnosis, development and retained assets

- Warmup hides short startup events; a rolling mean contaminated by event energy
  suppresses sustained sirens. The 6×/0.02 RMS gate misses quiet recordings. The
  original 1.3 s post-trigger wait produces windows unlike loudest 2.5 s training
  clips. Vote-only gates do not reject distant neighbors reliably.
- Implemented and compared adaptive-onset-only, periodic and hybrid scheduling.
  Shared one-second RMS-normalized training/inference windows, bounded 2 Hz
  classification, distance/vote rejection and episode deduplication were tested
  in the candidate; FFT/MFCC and balanced k=7 were retained.
- On 58 development files, raw routed recall rose 4/40 → 22/40 at zero background
  alerts, while quiet-prelude background alerts fell 10 → 0. This did not
  generalize. Full results and the frozen/rejection decision are in
  [evaluation/DECISION.md](evaluation/DECISION.md).
- Added 96 genuine licensed recordings: 47 additional training candidates,
  8 development backgrounds and 41 final recordings. Attribution, hashes and
  source/session groups cover all 316 assets. No synthetic training data.
- The data-only 217-file ablation also failed development: raw background alerts
  0 → 1, background-bed alerts 0 → 3. Its additions are kept in
  `datasets/additional-training/`, excluded from automatic installation and JAR
  training. The original 170 files are byte-identical and still bundled.

## Full evidence and reproduction

See [evaluation/README.md](evaluation/README.md) for exact commands, replay
construction, metric definitions and historical candidate reproduction.
`stream-baseline-final.{txt,tsv}` describes the retained model;
`stream-final.{txt,tsv}` describes the rejected candidate. Both contain direct
and streaming confusion matrices, per-class precision/recall, counts and actual
per-window predictions. Development and data-only reports are also committed.

Candidate runtime passed 332 mechanics/regression checks, real Swing tests and
real-file UI inference before acoustic rejection; a distance-gate mutation was
caught by the startup scheduling test. Reverting its behavior also reverted
its behavior-specific tests. Final retained-runtime verification passed:

- `java Build.java test`: **217 checks**, fresh trained startup/restart included.
- `java Build.java ui-test`: passed all display, keyboard, persistence and recovery
  assertions. KDE/Wayland focus was intermittent; a rerun passed after explicitly
  requesting window/tab focus and clicking the tab strip. No assertion was removed.
- `java -cp build/classes omniear.TrainedSmokeTest --ui`: actual development scream
  predicted scream/P0, vote 0.716, distance 0.981.
- `java Build.java jar`: successful warning-free Java 21 build.
- Portable JAR, fresh directory and restart: installed exactly 170 WAVs; real
  development explosion 256296 predicted explosion/P0, vote 0.764, distance 0.478.
- Dataset audit passed hashes, inventory, decoded uniqueness and logical
  training/development/final source separation; original 220 WAV hashes unchanged.
- `src/` and `Build.java` are byte-for-byte unchanged from `5a5b764` after rollback.
- `python3 scripts/test_replay_metrics.py`: passed prelude false-alert accounting,
  precision, exposure and insertion-latency regression assertions.

The five-second **physical microphone diagnostic**, separate from simulated
replay, captured/released input: RMS 0.0007, peak 0.0225, signal received, no
clipping reported. It did not validate event detection in a room. Candidate
`accept()` processing measured p95 8.844 ms / maximum 10.963 ms on this machine
(DSP+scheduling+k-NN; excludes CSV/UI/EOF). Low latency did not fix accuracy.

Remaining limitations: few independent explosion/siren sources, acted screams,
glass rather than vehicle collisions, poor background generalization, weak
timestamps, short repeated replay exposure, and unresolved live detection.

---
