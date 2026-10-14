# Team handoff — your first session with OmniEar

[START HERE](../README.md) → [Setup](GETTING_STARTED.md) → Team handoff → [Demo](DEMO.md)

## What we built and why

OmniEar explores whether an energy-triggered audio pipeline can flag/classify
environmental sounds. The Java version makes the pipeline inspectable for a CSE
OOP project: interfaces, inheritance, composition, records, collections,
exceptions, concurrency and persistence all have real jobs. It replaces the
upstream Python/React/TensorFlow implementation with Java Sound, Swing and Java
FFT/MFCC + k-NN; it does not reproduce upstream neural semantics or reliability.

You do **not** need to understand DSP before launching it. Finish
[Getting started](GETTING_STARTED.md), obtain `PASS: 261 checks`, open the GUI
and classify a bundled example. Then read [Architecture](ARCHITECTURE.md) while
following the classes in your editor.

## What works / what is not promised

| Verified working behavior | Important boundary |
|---|---|
| Automatic 170-WAV training install into a missing default root; reload/import controls | Coverage is not quality; existing roots are preserved and custom data changes predictions. |
| Actual direct predictions, scores and ten licensed GUI/JAR demos | Five selected matches and five failures are a teaching selection, not an accuracy benchmark. |
| Microphone/WAV input, RMS meter and gated processing | Continuous detection is unreliable; it may emit no event or a false alert. |
| Input diagnostic, Stop and resource cleanup | A device opening successfully does not prove room-event recognition. Current microphone/gain needs rehearsal. |
| Local event/alert tables and durable CSV restart | P0/P1/P4 are local labels; no remote response, audio playback or physical alarm is sent. |
| Regression suite, Swing/JAR and replay behavior checks on Linux/JDK 21 | Windows/macOS, screen readers and long-duration live stability are not verified here. |

Historical results use **different inspected sets**: original direct 36/50,
later direct 25/48 and raw routed target recall 1/18. Do not quote a favorable
subset as field accuracy. False positives remain, source diversity is limited,
most screams are acted, and impacts are mostly breakages rather than collisions.
All existing final sets have been inspected; further ML work needs a new untouched
set. See [TEST_RESULTS.md](../TEST_RESULTS.md) and [DATASETS.md](../DATASETS.md).

The verified cleanup was integrated into `main` at `87ff0fb9`: test-only search/
polling sharing and a constructor-local k-NN mean. Predictions, thresholds,
training assets and runtime features did not change. These onboarding documents
are proposed separately on `docs/team-onboarding` until their PR is reviewed.

## Where things live

```text
OmniEar-Java/
  Build.java             Java-only compiler/build/test/JAR entry point
  src/omniear/           Main + audio/detection/model/alerts/ui packages
  test/omniear/          Runnable regression and real-display smoke tests
  datasets/              Licensed WAVs + manifest, attribution and split metadata
  demo/                  Ten-file catalog, verified results, dated input evidence
  evaluation/            Frozen research protocols, decisions and results
  scripts/               Optional research/acquisition tools, not startup requirements
  docs/                  Setup, architecture, tests, demo, report and UML
  CONTRIBUTING.md        Branch/PR/review workflow
  build/                 Generated classes/JAR/renders (ignored)
  data/                  Default runtime training copies + events.csv (ignored)
```

`datasets/training` is the accepted 170-file bundle. `additional-training` is
research-only; `held-out`, `development`, `final-test` and `rejection-final`
contain inspected evaluation/development assets, not extra default training.
Keep their provenance and roles intact. Runtime `--data` can point elsewhere;
choose a new name rather than deleting existing sessions.

## Beginner vocabulary you will see in code/output

- **PCM / sample:** numeric audio amplitudes; 16 kHz means 16,000 samples/second.
- **Block / feature window:** a 100 ms monitoring chunk versus 2.5 s for classification.
- **RMS / baseline:** digital energy level versus recent typical energy; not dB SPL.
- **FFT / MFCC:** frequency analysis and a compact spectral description, not intent detection.
- **k-NN / vote:** compare features to nearby labeled training recordings; votes are not probabilities.
- **EDT / SwingWorker:** GUI's event thread versus background work; never block the EDT with DSP/audio I/O.
- **Event / alert:** an event can be suppressed or untrained; only `ALERT` is shown in Alert History.
- **`NO_EVENT`:** the gated route emitted nothing, not a prediction of background.
- **PR / commit:** reviewable branch proposal versus a saved unit of change in git.

## Suggested code-reading route and study split

1. [`Main.java`](../src/omniear/Main.java) and [`Build.java`](../Build.java): actions, paths and how the app starts.
2. `AudioSource` → `PcmSource` → `WavSource`/`MicrophoneSource`: polymorphism and shared conversion.
3. `MonitorService` → `AdaptiveDetector` → `DetectionPipeline`: worker ownership, RMS and capture.
4. `TrainingRepository` → `FeatureExtractor` → `KnnClassifier`: real data to predictions.
5. `AlertPolicy` → `SoundEvent` → `EventStore`: statuses, validation and persistence.
6. `Dashboard` → `TrainingPanel`/`WavTestingPanel` → `EventTableModel`: worker-to-EDT presentation.

For three teammates, a useful **study** split is input/detection, model/storage,
and UI/testing. These are not isolated ownership silos: pair up on a PR and have
each person explain one sound's entire path. Keep changes small enough that
another teammate can follow the whole diff. [UML](UML.md) and
[Architecture](ARCHITECTURE.md) tie the packages together.

## Demonstration checklist

Use the [canonical five-minute walkthrough](DEMO.md), including the five-class
table and CLI fallback; do not invent a separate "better" demonstration.

1. Before presenting, build/test/JAR and rehearse on that laptop with a new
   `--data` name. Wait for 170 WAVs / five classes and show 40/18/40/32/40 counts.
2. In WAV Testing show a real direct match for **each of the five categories**.
   Show provenance and scores, then the wind → scream/P0 false alert too.
3. Explain that raw replay of these ten demos produces no events with default
   calibration; a microphone waveform is not proof of recognition.
4. If permitted, run the five-second microphone diagnostic; speak normally,
   check OS input/gain, Stop/release. Never enact dangerous events.
5. Show Dashboard versus Alert History and close/reopen the same session to
   demonstrate CSV persistence. Show UML; acknowledge limitations.

If something fails, keep the error and use [Testing](TESTING.md) /
[setup troubleshooting](GETTING_STARTED.md#troubleshooting). Report what was not
demonstrated. Typical viva questions and short source-backed answers live in
[Demo → viva questions](DEMO.md#likely-viva-questions), with the academic narrative
in [Project report](PROJECT_REPORT.md).

## Safe starter contributions

First agree on scope and use [Contributing](../CONTRIBUTING.md); do not all edit
the same files on `main`.

| Small task | Evidence to leave |
|---|---|
| Rehearse Windows/macOS startup and clarify OS-specific instructions | Real OS/JDK, exact commands/errors and results; no claims for untested platforms. |
| Improve an existing error explanation or navigation link | Confirm actual behavior/class/control, review links and run documented commands. |
| Add one missing regression for an existing WAV/CSV boundary | Reproduce expected behavior, use the current test helpers, no new framework. |
| Improve a label/accessible name without altering state flow | Real-display tests and render inspection; preserve keyboard/focus assertions. |
| Explain an existing class relationship in UML/report | Trace callers and verify it against source; no speculative new layer. |

Do not start by tuning thresholds/k/window timing, adding model caching or
dependencies, changing CSV format, deleting research evidence, relabeling data,
or importing demos into training. Those need separate proposals and stronger
validation. Your first PR should make the project easier to use or verify,
not silently change what it predicts.
