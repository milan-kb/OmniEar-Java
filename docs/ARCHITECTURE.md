# Architecture: follow one sound through the code

[START HERE](../README.md) · [Setup](GETTING_STARTED.md) · [UML](UML.md) · [Report](PROJECT_REPORT.md)

OmniEar is a Java 21/JDK-only desktop prototype. It combines object-oriented
audio input, a two-stage detection pipeline, real WAV-trained classification,
local CSV storage and Swing presentation. The Java replacement and its upstream
boundaries are recorded in [INSPECTION.md](../INSPECTION.md); it is **not YAMNet**.

## Source map

Names below live under [`src/omniear/`](../src/omniear/); filenames match class names.

| Package | Classes to read | Responsibility |
|---|---|---|
| `omniear` | `Main` | Parses CLI flags; starts headless actions or schedules `Dashboard` on the EDT. |
| `audio` | `AudioSource`, `PcmSource`, `MicrophoneSource`, `WavSource` | Acquire, decode, downmix and resample input; manage stream/device lifetimes. |
| `audio` | `AudioSamples`, `InputDiagnostics` | RMS/window selection; read-only decoded input-level measurements. |
| `detection` | `AdaptiveDetector`, `DetectionPipeline`, `MonitorService` | Energy gate, sample-clock capture ring, single audio worker and callbacks. |
| `model` | `FeatureExtractor`, `FeatureVector`, `TrainingExample`, `SoundClassifier`, `KnnClassifier` | Turn PCM into numeric features and a neighbor-based prediction. |
| `model` | `TrainingRepository`, `Category`, `SoundEvent` | Install/load/import training; define class rules and immutable event data. |
| `model` | `DemoSample`, `HeldOutEvaluation` | Licensed demo resources; separate labeled direct/replay evaluation. |
| `alerts` | `AlertPolicy`, `Priority`, `EventStore` | Decide alert/suppression; append, lock and reload metadata CSV. |
| `ui` | `Dashboard`, `TrainingPanel`, `WavTestingPanel` | Coordinate controls, model readiness, background work and results. |
| `ui` | `WaveformPanel`, `EventTableModel`, `DarkTheme` | Render actual PCM, bounded event history and standard Swing styling. |

[`Build.java`](../Build.java) compiles both `src` and `test` using the JDK compiler,
packages the curated training ZIP/demo resources, and launches checks or the app.
It does not fetch libraries. Tests are ordinary Java `main` programs, not JUnit.

## Two routes, not two equally reliable demonstrations

**Classroom Demo** adds a guided presentation of the existing **direct** route:
`Main --demo` (or Dashboard launcher) → `ClassroomDemo` → `DemoSample.load()` →
`ClassroomSession.classify()` → unchanged `DetectionPipeline.classifyClip()` /
`AlertPolicy` → `EventStore` → Swing result/history. `WavPlayback` writes the same
decoded recording to a Java Sound `SourceDataLine`; speaker gain applies only
to those output bytes. Playback does not feed a microphone or generate alerts.
A single worker owns model loading, inference, CSV and playback; EDT owns widgets.
Stop closes output, while the already persisted result remains. Reset rotates
to a fresh append-only CSV in an isolated session; old CSVs and normal user data
are retained. No new classifier, training or acoustic gates are introduced.

```text
MicrophoneSource OR WavSource
          |
PcmSource: decoded mono samples at 16,000 Hz
          |
MonitorService: read up to 1,600 samples (100 ms)
          |
DetectionPipeline -> AdaptiveDetector: warmup + RMS threshold
          | trigger only
3.5 s ring + post-trigger wait -> FeatureExtractor -> KnnClassifier
          |
AlertPolicy -> SoundEvent -> EventStore CSV + listener -> Dashboard / CLI

Direct WAV / bundled example
          |
WavSource.load / DemoSample.load
          |
DetectionPipeline.classifyClip (NO warmup or energy gate)
          |
FeatureExtractor -> KnnClassifier -> AlertPolicy -> CSV + result
```

`--analyze` and **Start monitoring** use the first route. `--classify` and
**Classify example** use the second. A correct direct prediction does not prove
that the continuous route would trigger, capture the right audio or classify it
correctly. WAV processing is faster than real time and **does not play sound**.

## 1. Input and RMS: deciding whether to inspect a sound

**PCM** is a sequence of numeric amplitude samples. `PcmSource` converts supported
integer/float samples to normalized amplitudes, averages channels into mono,
and uses an anti-aliasing sinc resampler for non-16-kHz input. This is sample-rate
and channel conversion, **not per-clip volume/gain normalization**.

`AudioSamples.rms()` measures block energy: `sqrt(sum(sample²) / count)`.
`AdaptiveDetector` uses the mean of the previous up to 50 block RMS values as
its baseline. Defaults require 20 warmup blocks (2 s), then:

```text
trigger = RMS > max(6 × baseline, 0.02)
          AND time since last trigger > 1.5 seconds
```

Both comparisons are strict. The current block enters the baseline **after**
comparison, including when it triggered. Audio time comes from samples, not
worker speed. The detector finds energy anomalies, not semantic emergencies;
quiet events can be missed and sustained noise can lift the baseline.

`DetectionPipeline` keeps 56,000 samples (3.5 s). After a trigger it waits at
least 20,000 more samples (1.25 s, normally 1.3 s at block boundaries), then
classifies captured audio. WAV EOF flushes pending capture; manual Stop discards
unfinished capture. The event timestamp is based on the trigger's audio offset.

## 2. FFT and MFCC: representing the sound with 60 numbers

`FeatureExtractor.extract()` uses `AudioSamples.loudestWindow()` to select
2.5 s by rolling sums of 100 ms RMS frames; shorter clips are centered/zero-padded.
Training and inference use this same extractor.

Each window is divided into **25 ms frames**, stepping **10 ms** at a time:

1. Measure raw RMS and zero-crossing rate (how often the sign changes).
2. Remove the frame's DC mean for the spectrum, apply a Hann window, then run
   the **512-point FFT**. FFT expresses a short waveform as frequency components.
3. Compute spectral centroid, bandwidth, 85% rolloff, flatness and positive flux:
   rough descriptors of frequency location, spread, noise-like shape and change.
4. Apply **26 triangular mel filters**, log their energies, then DCT-II to obtain
   **13 MFCCs**, including coefficient zero. MFCCs compactly describe spectral
   shape; they do not understand distress, intent or an emergency's cause.
5. Pool each of the 20 frame features as mean, maximum and population standard
   deviation: **20 × 3 = 60 dimensions** in a validated, copied `FeatureVector`.

Pooling loses original frame order. This is handcrafted DSP, not pretrained
semantic embeddings; upstream neural weights cannot be used with these features.

## 3. Training and k-NN: comparing to known recordings

`TrainingRepository.installBundled()` seeds **only an absent default training
root** with 170 real WAVs. `load()` visits sorted files under category-ID folders,
extracts features, deduplicates byte-identical files and rejects conflicting labels.
All five classes need at least one usable non-silent file before classification
is enabled. That minimum establishes coverage, not quality.

Training WAVs persist; there is no serialized neural-model file or feature cache.
Startup/reload rebuilds the in-memory model from recordings. Explicit import
validates a staged copy, names it by SHA-256 and leaves the original untouched.
A failed GUI reload clears the active classifier rather than keeping stale readiness.

`KnnClassifier` keeps up to **k=7 nearest examples** in a priority queue. For each
feature dimension it derives a population standard-deviation scale from training
only. Its distance is `sqrt(mean(((query[i] - example[i]) / scale[i])²))`.
Near-zero-variance dimensions use scale 1. Closer neighbors get inverse-distance
votes, divided by their training class counts to compensate for uneven coverage.
If an exact neighbor exists, non-exact neighbors do not vote.

The winner's share of total votes is **neighbor agreement**, not a calibrated
probability or accuracy. Lower nearest distance means closer features, not proof
of a meaningful match. Search remains O(N × 60); no speculative index is present.
The cleanup made the constructor-only mean array local without changing arithmetic.

## 4. Alert rules and persistence

| `Category.id()` | Bundled WAVs | Gate (inclusive) | Routed priority |
|---|---:|---:|---|
| `scream_distress` | 40 | 0.52 | P0 / Critical |
| `explosion` | 18 | 0.65 | P0 / Critical |
| `impact_crash` | 40 | 0.69 | P1 / High |
| `siren_traffic` | 32 | 0.80 | P4 / Monitoring |
| `background` | 40 | Never alerts | NONE / Suppressed |

`AlertPolicy` creates `SoundEvent` with `ALERT`, `BACKGROUND_SUPPRESSED`,
`LOW_VOTE_SUPPRESSED` or `UNTRAINED`. No model means no invented category or alert.
Priorities are local metadata, **not SMS, sound playback or remote dispatch**.

`EventStore` owns an exclusive file lock, writes quoted UTF-8 CSV, forces appends
to disk and validates history on reopen. Corrupt history is not discarded; failed
writes halt further appends until repair/reopen. GUI open failure gives explicit
memory-only mode. Dashboard shows all events; Alert History filters `ALERT` only.
GUI/store retain at most 1,000 events in memory; CSV is not truncated. Statistics
describe that retained window, not lifetime counts. See [CSV check](TESTING.md#csv-persistence-check).

## 5. Swing threading and cleanup

| Thread / mechanism | Work and rule |
|---|---|
| EDT (event-dispatch thread) | Owns widgets/table mutations; keep handlers short. `Main` schedules GUI creation here. |
| `MonitorService` single executor | Audio reads, detection, inference and CSV writes in order. Listener callbacks may come from this worker. |
| `SwingWorker` in training/testing panels | Heavy import, feature extraction, clip classification/evaluation in `doInBackground`; `process`/`done` update GUI on EDT. |
| `AtomicReference` + 80 ms Swing timer | Coalesces the latest waveform block instead of queueing every block onto the EDT. |
| Close/shutdown threads | Close the device to unblock reads, cancel panel work, wait for audio termination and release CSV ownership. |

`Dashboard` marshals worker notifications with `SwingUtilities.invokeLater`.
Do not move audio/DSP/file I/O into button handlers or update Swing from workers.
Model import/reload is disabled during monitoring/clip work. Slow DSP/disk can
still overrun the microphone's half-second buffer: this is not hard real time.
The test-only `SwingTestSupport` searches on the EDT and polls EDT readiness from
the test thread; it is not a production UI abstraction.

## Concrete OOP, not decorative inheritance

| Principle | Where it earns its place |
|---|---|
| Abstraction | `AudioSource` defines input operations; `SoundClassifier` defines classification independently of the UI. |
| Inheritance | WAV/microphone extend abstract `PcmSource`; only stream acquisition differs. Swing subclasses reuse toolkit behavior. |
| Polymorphism | One monitor consumes either input; one pipeline calls the classifier interface; CLI/dashboard use listener callbacks. |
| Encapsulation | Private detector/ring/store state; copied feature arrays/model lists; constructors enforce valid values. |
| Composition | Dashboard owns panels/monitor; pipeline combines detector, extractor and policy. |
| Records, enums, collections | Immutable event/prediction records; constrained categories/statuses; deque baseline, lists, enum maps and neighbor queue. |
| Resource ownership | `AutoCloseable`, checked I/O exceptions and try-with-resources make failure and cleanup explicit. |

Supported WAVs: integer PCM8/16/24/32 or float32/64, 8–192 kHz, 1–8 channels,
up to 120 s; whole-clip loading needs at least 100 ms. Import size limit is 200 MB,
training limit 5,000 usable WAVs. MP3/OGG/FLAC/compressed WAV are unsupported.
Microphone input tries OS-default PCM16 at 16/48/44.1 kHz, mono then stereo.
Raw monitoring audio is never stored/transmitted; explicit training imports save
the selected WAV copies. Licensing: [DATASETS.md](../DATASETS.md).

Use [UML](UML.md) for relationships, [Project report](PROJECT_REPORT.md) for the
academic narrative, and [Testing](TESTING.md) before changing any subsystem.
