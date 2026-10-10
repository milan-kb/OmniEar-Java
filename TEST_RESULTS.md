# Verification results

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
