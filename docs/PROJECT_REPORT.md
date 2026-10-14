# OmniEar: Java OOP acoustic-monitoring project

[START HERE](../README.md) · [Setup](GETTING_STARTED.md) · [Architecture](ARCHITECTURE.md) · [Testing](TESTING.md) · [Team handoff](TEAM_HANDOFF.md)

## Objective and scope

OmniEar is an offline Java 21 desktop application demonstrating object-oriented
design, audio processing, concurrency and durable file I/O. It accepts the OS
default recording input or PCM WAV files, detects energy anomalies, classifies
captured audio into five categories and records local alert metadata. A separate
WAV inspection route bypasses anomaly detection. Swing and Java Sound are part
of the standard JDK; no runtime dependencies or build-system downloads are needed.

The academic objective is a working, explainable software pipeline, **not a
validated emergency detector**. There is no remote dispatch, alarm hardware,
neural network, YAMNet compatibility, sound playback or network service. The
semester-submission changes improve demonstration and diagnostics, not the ML
model. Earlier unsuccessful experiments and their evidence remain in git and
`evaluation/`.

## Architecture and data flow

1. `Main` parses CLI actions or creates `Dashboard` on Swing's event-dispatch
   thread (EDT). `TrainingRepository` installs the bundled model only when the
   default training directory is absent, then extracts features from WAVs.
2. `MicrophoneSource` and `WavSource` share `PcmSource` decoding/downmixing and
   resampling. Both expose the `AudioSource` contract: open, read, describe, close.
3. `MonitorService` owns one audio executor. It feeds 100 ms blocks to
   `DetectionPipeline`, which combines `AdaptiveDetector`, a bounded capture
   ring, `FeatureExtractor`, a `SoundClassifier` and `AlertPolicy`.
4. `KnnClassifier` supplies the actual classification implementation.
   `SoundEvent` records predictions, scores, timing, priority and suppression
   status. `EventStore` persists metadata; `Dashboard` displays it.
5. `WavTestingPanel` runs direct classification/evaluation in `SwingWorker` jobs.
   `DemoSample` loads licensed, inspected non-training examples from JAR
   resources. Dataset labels are shown as provenance; match/mismatch is computed
   after inference, and labels are never passed to the classifier.
   `TrainingPanel` explicitly imports labeled recordings.

The [UML diagram](UML.md) shows source-level relationships rather than a proposed
architecture. The [architecture guide](ARCHITECTURE.md) explains the pipeline for
new teammates; the [feature audit](FEATURE_AUDIT.md) maps controls to implementation.

## Object-oriented concepts, with concrete examples

| Concept | Actual implementation and purpose |
|---|---|
| Classes/objects | One `MonitorService` per dashboard owns its active source and worker. `EventStore` owns an open channel/lock. |
| Interfaces | `AudioSource`, `SoundClassifier`, `MonitorService.Listener`; `AutoCloseable` contracts support deterministic cleanup. |
| Abstraction | `PcmSource` is abstract; `openStream()` varies while final `open()`/`read()` enforce shared decoding behavior. |
| Inheritance | `WavSource` and `MicrophoneSource` extend `PcmSource`; dashboard/panels extend Swing frame/panel classes; `EventTableModel` extends `AbstractTableModel`. |
| Polymorphism | The same monitor consumes either `AudioSource`; the pipeline invokes `SoundClassifier.classify()`; listener callbacks serve the GUI or CLI without a Swing dependency in the monitor. |
| Encapsulation | Detector history, capture ring and store channel are private. `FeatureVector` validates finite dimensions and copies arrays on input/output. The k-NN model copies its example list. |
| Composition | Dashboard owns panels and monitor; the pipeline combines detector, extractor and alert policy. Inheritance is used for real shared behavior, not arbitrary class hierarchies. |
| Records/enums | `SoundEvent`, `TrainingExample`, `Prediction`, `Config`, `Reading`, `DemoSample` are data records. `Category`, `Priority` and event `Status` constrain values. |
| Validation | Constructors reject inconsistent events/configurations and incomplete training coverage. Checked `IOException`s report device, WAV and persistence errors. |

There is one production classifier implementation. The interface is a genuine
pipeline boundary, not a claim of multiple trained algorithms being selectable.

## Collections and algorithms

- `ArrayDeque<Double>` and a running sum maintain the previous 50 RMS blocks.
  Anomaly detection waits 20 blocks and triggers only when RMS exceeds
  `max(6 × baseline, 0.02)` and elapsed cooldown is strictly greater than 1.5 s.
  Current energy is inserted after comparison. These defaults are unchanged.
- A 56,000-sample circular buffer holds up to 3.5 s. Capture waits at least 1.25 s
  after triggering (normally 1.3 s at block boundaries); EOF flushes a pending
  capture. Direct classification skips this gate and capture delay.
- `AudioSamples.loudestWindow()` selects a 2.5 s window using sums of 100 ms RMS
  frames. Short clips are centered and zero-padded. There is no gain normalization.
- Feature extraction uses 25 ms frames, 10 ms hop, Hann windows and a 512-point
  radix-2 FFT. Seven energy/spectral features plus 13 MFCCs are pooled as
  mean/max/standard deviation to produce 60 dimensions. Mean/max/deviation do
  not retain the original temporal order or establish semantic context.
- `PriorityQueue` retains the nearest k=7 examples. Distances use training-only
  standard-deviation scales; inverse-distance votes are divided by class counts.
  Exact matches vote only among exact neighbors. Search visits all N examples
  (60 dimensions), with O(log k) heap operations and O(k) neighbor storage.
- `EnumMap` counts classes, `EnumSet` checks coverage, `HashMap`/`HashSet` track
  duplicate hashes and evaluation overlap. `ArrayList` stores examples/events;
  immutable list copies protect callers from modifying model/store state.
- `AlertPolicy` maps scream/explosion/impact/siren to P0/P0/P1/P4 when their vote
  gates pass (0.52/0.65/0.69/0.80). Background and low votes are suppressed.
  These are routing rules, not probabilities of an emergency.

## Threading and lifecycle

The EDT owns Swing widgets and table mutations. Audio reading, DSP, inference
and CSV appends run sequentially on one executor; training and clip/evaluation
work use `SwingWorker`. An `AtomicReference` coalesces waveform blocks for an
80 ms Swing timer rather than queueing every block. `volatile` and synchronized
lifecycle state coordinate stop/close. Closing the microphone line on another
thread unblocks reads; worker cancellation and `try`-with-resources release files.
Controls prevent model reload/import during monitoring or clip work.

This is bounded, simple concurrency, not a hard real-time guarantee. Slow disk
writes or a large training set can overrun the half-second microphone buffer.
Window-manager keyboard focus is also an OS integration concern.

## File I/O, persistence and failure handling

PCM WAV decoding supports integer 8/16/24/32-bit and float32/64, 8–192 kHz,
1–8 channels. Downmixing averages channels; a streaming sinc low-pass resampler
handles non-16-kHz input. WAVs are limited to 120 s; whole-clip loading requires
at least 100 ms. Training import validates a staged copy, checks SHA-256 overlap,
then moves it under its category. Original input files are not edited.

`EventStore` quotes UTF-8 CSV fields, validates records, holds a process file lock
and forces writes to disk. It keeps the last 1,000 records in memory without
truncating the CSV. Open failure produces visible memory-only GUI mode; an append
failure reports the unsaved event and halts further writes until repair/reopen.
No raw microphone audio is saved. Explicit imports persist selected WAV copies;
demo resource decoding uses a temporary WAV deleted in `finally`.

## Demonstration and evidence

The unchanged model contains 170 genuine licensed training recordings, class
counts 40/18/40/32/40. Ten separate, already-inspected demo files include a
correct direct prediction and a known failure for each class. They are licensed
CC BY 3.0 or CC0; author/source/license are visible in the GUI and catalog. They
are packaged separately and never automatically enter training.

`SubmissionDemoTest` recomputes all ten outcomes with the actual bundled model,
then replays each raw WAV through the default pipeline. The selected set gives
five matching labels and five mismatches; **all ten raw replays emit no event**.
This deliberate teaching selection is not an accuracy estimate. The wind example
is a real false scream/P0 alert in direct mode. See
[`demo/VERIFIED_RESULTS.tsv`](../demo/VERIFIED_RESULTS.tsv) and the
[five-minute walkthrough](DEMO.md).

Broader historical results remain in [TEST_RESULTS.md](../TEST_RESULTS.md): the original inspected
50-file direct result was 36/50, while a later 48-file test gave 25/48 and raw
routed target recall 1/18. Never substitute the favorable earlier result or a
successful demonstration for a live-detection reliability claim. Most screams
are acted, impacts are mainly breakages rather than crashes, and explosion/siren
source coverage is limited. Microphone RMS/dBFS and near-full-scale percentage
measure decoded input levels, not acoustic dB SPL or recognition performance.

## Integrated cleanup and teammate verification

The behavior-preserving cleanup is integrated into `main` at `87ff0fb9`.
Six Swing searches share one test-only typed traversal; three readiness loops
share EDT polling with original waits/assertions. The k-NN constructor's mean
array is now local; calculations, order, training assets and predictions are unchanged.
No production algorithm change or new production abstraction was introduced.

The suite passes **261 checks** (249 original + twelve helper regressions).
JAR/help/ten-demo checks, real-display Swing suites, fresh startup/restart, CSV
reopen and cleanup passed on Linux/JDK 21. Cleanup replay comparisons reproduce
1,370 development and 671 final non-header TSV rows byte-for-byte; this is
behavior verification, not a new acoustic accuracy claim. No assertions were skipped.

[Getting started](GETTING_STARTED.md) documents Windows/macOS/Linux setup;
those other operating systems still need runtime verification. Teammates use
[Contributing](../CONTRIBUTING.md) for reviewed small changes. Microphone permission,
input gain and long-running field behavior need actual-machine checks; the
onboarding documentation does not expand the verified recognition scope.

## Conclusion

The submission demonstrates working Java OOP architecture, real audio processing,
local persistence and an auditable UI. Its software behavior can be demonstrated
reproducibly, including error recovery and genuine classifier failures. Reliable
emergency detection remains outside the verified scope; no new ML optimization
or algorithm changes were made for this submission.
