# OmniEar — Java OOP semester project

A working, offline desktop acoustic-monitoring application using **Java 21 and
only the standard JDK**. Swing, Java Sound, collections, concurrency, CSV file
I/O and Java-implemented DSP replace the upstream Python/React/TensorFlow stack.
The upstream repository is untouched. See [INSPECTION.md](INSPECTION.md) for the
inspected commit, repository inventory and preserved/replaced behavior.

**This is not YAMNet and is not a validated emergency-response system.** No
pretrained model or real training audio is bundled. No classification accuracy,
precision, recall or evaluation score is claimed. Missing training data produces
**unclassified anomalies**, not made-up categories or background predictions.

## Setup and execution

Install a full **JDK 21** and make `java` available on PATH. A graphical desktop
and OS microphone permission are needed for live capture; headless WAV analysis
and training also work. No Maven, Gradle, downloads or dependencies are required.

On CachyOS/Arch with fish, install a permanent JDK (do not use a `/tmp` JDK):

```fish
sudo pacman -S --needed jdk21-openjdk
fish_add_path /usr/lib/jvm/java-21-openjdk/bin
```

Run these commands **inside `OmniEar-Java/`**, on Linux, macOS or Windows:

```sh
java -version
java Build.java build       # --release 21, -Xlint:all, -Werror
java Build.java test        # executable regression/integration checks
java Build.java run         # build and open Swing dashboard
```

Alternatively create a runnable JAR:

```sh
java Build.java jar
java -jar build/OmniEar.jar
```

The build helper is itself Java. The JAR has no external runtime dependencies;
JDK-provided audio/desktop OS integration is the only platform integration.
Runtime files default to `data/` relative to the current working directory.

```sh
# Optional paths; existing original organized WAV datasets can be read directly.
java Build.java run --data /path/to/runtime-data --training /path/to/labeled-wavs

# Headless commands after building:
java -cp build/classes omniear.Main --train /path/to/labeled-wavs
java -cp build/classes omniear.Main --analyze /path/to/recording.wav --training /path/to/labeled-wavs
java -cp build/classes omniear.Main --classify /path/to/clip.wav --training /path/to/labeled-wavs
java -cp build/classes omniear.Main --evaluate /path/to/held-out --training /path/to/labeled-wavs
java -cp build/classes omniear.Main --mic-test
java -jar build/OmniEar.jar --help
```

`--train` loads/checks real WAVs and builds an in-memory model; it does not invent
examples, save a neural network or evaluate accuracy. Nonzero exit status means
invalid input, missing required training coverage or an I/O error.

## Dashboard workflow

The dark Swing interface has four tabs: **Dashboard**, **WAV Testing**,
**Training**, and **Alert History** (Alt+1 through Alt+4). The Dashboard's
signal/event divider can be dragged; long training content and tables scroll.
The design uses a flat charcoal palette, readable standard fonts, teal readiness
indicators, and text plus severity colors. No third-party UI library is used.

1. **Train first** for classification, or start without training to inspect RMS
   anomalies. The missing-model state and per-category WAV counts are visible.
2. **Start monitoring** opens the laptop's OS default input. Stay quiet for the
   first two seconds. RMS, rolling baseline, threshold, elapsed audio time and
   the actual last 100 ms waveform update live. **Stop** closes the microphone.
   **Test microphone · 5 s** checks input RMS/peak and reports quiet/clipped audio;
   it stops automatically and saves no audio or events. Stop also cancels it.
3. In **WAV Testing**, **Analyze WAV** runs the same gated detector as the microphone. Files are
   analyzed faster than real time, not played back; timing uses sample offsets.
   The first two seconds calibrate. A very short or constantly loud file may
   correctly produce no Stage 1 event.
4. **Classify WAV** explicitly bypasses Stage 1 and uses the loudest 2.5 s
   window. Use this for short isolated sounds without a quiet calibration prelude.
   Its source is marked `[clip; Stage 1 bypass]`; baseline/threshold are zero,
    rather than fabricated calibration values. It is enabled only when trained.
   The result includes vote share, nearest standardized feature distance, duration,
   short-clip padding and exact-match diagnostics. Lower distance means closer
   training features, not a calibrated similarity probability.
   **Evaluate held-out folder** tests separate labeled recordings and reports
   overlap exclusions, per-file predictions and a confusion matrix; see [DATASETS.md](DATASETS.md).
5. **Detected events** includes alerts, suppressed background/low-vote outcomes
   and unclassified anomalies. **Alert history** filters to emitted P0/P1/P4
   alerts. Tables show UTC timestamps, audio offsets, category, severity, RMS,
   vote share and outcome; columns sort and scroll. Statistics use actual retained
   events, not synthetic city/fleet data. Latest-alert text includes severity.
6. Adjust the threshold multiplier, absolute floor and cooldown while stopped.
   Defaults match upstream. Keyboard Tab navigation and Alt+button mnemonics
   are available; labeled controls expose Swing accessible names. Severity is
   expressed in text, not color alone.

## Real WAV training/import workflow

Select a category and **Add labeled WAVs…**; multiple recordings can be imported
at once. Java validates staged copies, extracts features and rebuilds k-NN in a
background worker. Original files are never changed. The copies persist in the
**active training folder**, named by SHA-256 to avoid repeated imports. A byte-
identical WAV already imported under another category is rejected as conflicting.
Silent, malformed or unsupported examples are reported, not replaced. Five
per-class readiness rows, an import/reload log and progress indicator explain
what is missing. Reload also deduplicates manually named WAVs and refuses
conflicting byte-identical labels rather than silently biasing k-NN votes.

At least one usable, non-silent recording is required for **every category**,
including background, before predictions are enabled. One per class is only a
mechanical minimum, **not evidence of a useful classifier**. Use diverse,
balanced recordings from different sources, rooms, speakers and levels. Include
hard negatives: speech, music, laughter, applause, doors and ordinary traffic.
Never record dangerous events just to obtain training examples.

You may also organize existing licensed recordings manually:

```text
data/training/
├── scream_distress/    # real labeled distress/scream WAVs
├── explosion/          # real labeled explosion WAVs
├── impact_crash/       # real labeled crash/impact WAVs
├── siren_traffic/      # real labeled traffic-siren WAVs
└── background/         # real labeled non-threat WAVs
```

**Use training folder…** selects such a root, including an existing upstream
`data/organized/` directory if you already have it. **Reload training** reads it
without modifying recordings. Importing additional labeled WAVs writes only
explicit copies to the selected root. Nested WAV directories within each class
are supported. Bad examples are skipped with diagnostics; uncovered classes
leave the model untrained. Features are recomputed at startup/reload, ensuring
training and inference always use the same extractor.

The upstream Keras head expects 3,072 YAMNet embedding features, not these Java
features. Its HDF5 weights, TensorFlow semantic-fusion logic and upstream
evaluation numbers **cannot be reused as a Java k-NN model or evaluation**.
Verified public download links, licenses, label mappings and split preparation
for FSD50K, ESC-50 and NIGENS are in [DATASETS.md](DATASETS.md).

## Detection and classification

### Stage 1 (preserved)

- 16,000 Hz normalized mono, 1,600 samples / 100 ms per block.
- RMS: `sqrt(sum(sample²) / sampleCount)`; no per-clip gain/peak normalization.
- Previous 50 RMS blocks form a rolling arithmetic baseline; 20 blocks warm up.
- Trigger only if `RMS > max(baseline × 6, 0.02)` and cooldown is **strictly
  greater than** 1.5 seconds. Every block, including anomalies, enters the baseline
  **after** comparison, exactly as upstream.
- Capture a bounded 3.5 s in-memory ring and wait at least 1.25 s after the trigger
  block. Completion is quantized to 100 ms block boundaries (normally 1.3 s).
- Training and inference select the loudest 2.5 s using rolling sums of 100 ms
  RMS frames; short clips are centered and zero-padded. WAV EOF flushes pending
  capture; manual Stop discards unfinished capture. Event time is the trigger
  block time, not the later classification time.

### Stage 2 (replacement)

- 25 ms frames, 10 ms hop, DC removal for spectra, Hann window, 512-point radix-2
  FFT. Raw RMS is kept before spectral DC removal.
- 20 frame features: RMS, ZCR, spectral centroid, bandwidth, 85% rolloff,
  flatness, positive spectral flux and **13 MFCCs** (26 triangular mel filters,
  log energies, orthonormal DCT-II, including coefficient zero).
- Concatenated mean/max/population-standard-deviation pooling gives **60
  features**, preserving temporal variability and transients.
- k-NN uses `k=3`, Euclidean distance with training-only standard-deviation
  scaling and inverse-distance votes. Exact matches vote only among exact
  neighbors. Immutable models are replaced only after successful reload.
- **Vote share is a neighbor-agreement score, not a calibrated probability,
  accuracy estimate or certainty that an emergency occurred.** There is no
  pretrained semantic evidence or out-of-distribution rejection.

| Category | Alert priority | Initial minimum vote share |
|---|---|---|
| Distress scream | P0 / Critical | 0.52 |
| Explosion | P0 / Critical | 0.65 |
| Impact/crash | P1 / High | 0.69 |
| Traffic siren | P4 / Monitoring | 0.80 |
| Background | Suppressed | Never alerts |

These gates retain the upstream values for continuity, **not** its confidence
calibration. Calibrate them on genuinely held-out recordings before making any
performance claim. Keep related/augmented versions of a source recording in the
same split; do not treat training-set matches as classification accuracy.

## Architecture and meaningful OOP

```text
omniear.Main
  ui.Dashboard / TrainingPanel / WavTestingPanel / WaveformPanel / EventTableModel
        │                           │
  detection.MonitorService    model.TrainingRepository
        │                           │
  audio.AudioSource           model.FeatureExtractor → KnnClassifier
    ├─ PcmSource ← WavSource         │
    └─ PcmSource ← MicrophoneSource │
        │                           │
  detection.AdaptiveDetector → DetectionPipeline
        │
  alerts.AlertPolicy → model.SoundEvent → alerts.EventStore (CSV)
```

| OOP concept | Meaningful use |
|---|---|
| Encapsulation | Private ring/baseline state; immutable, defensively copied `FeatureVector`; private table history |
| Abstraction | `PcmSource` shares decoding/resampling and declares abstract `openStream()` |
| Inheritance | WAV and microphone subclasses supply different stream acquisition; panels/table/frame extend Swing classes |
| Polymorphism/interfaces | `AudioSource` inputs run through one monitor; `SoundClassifier` contract; `MonitorService.Listener` delivers dashboard/CLI notifications |
| Constructors | Validate configuration, feature vectors, training coverage, event invariants and storage ownership |
| Collections | `ArrayDeque` baseline; lists of training examples/events; `EnumMap` counts; priority queue for nearest neighbors |
| Exceptions/file I/O | Checked audio/I/O failures, strict CSV validation, staged imports, try-with-resources, readable diagnostics |
| Multithreading | Audio executor, SwingWorker training/clip jobs, asynchronous device close, atomic coalesced waveform updates, EDT-only UI mutations |

The audio worker performs detection, inference and event persistence sequentially;
there is no unbounded inference queue. The microphone line has a half-second
buffer. Large workloads or slow disks can overrun that buffer; separate bounded
inference/storage workers would be the next step if measured latency requires it.
Training is disabled during monitoring so model changes cannot race a session.
`HeldOutEvaluation` owns leakage checks and evaluation; `DarkTheme` owns Swing
colors/typography. Neither changes the detector, FFT/MFCC or k-NN algorithms.

## Audio, persistence and cleanup

- Java Sound reads PCM WAV: signed/unsigned 8/16/24/32-bit integers or 32/64-bit
  floating point, 8–192 kHz, 1–8 channels. Channels are averaged into mono.
  A streaming Hann-tapered sinc low-pass resampler suppresses aliasing before
  conversion to 16 kHz. Native 16 kHz PCM is decoded directly.
- Microphone capture tries OS default 16 kHz, 48 kHz and 44.1 kHz signed PCM16,
  mono then stereo. Unsupported/denied devices show a helpful error; WAV mode
  remains usable. No extra hardware, downloaded native libraries or network calls.
- WAV clips are limited to 120 s; imports to 200 MB; training to 5,000 usable
  WAVs. Compressed WAV, MP3/OGG/FLAC are unsupported. Integer normalization uses
  full-scale divisors; nonfinite float samples and truncated PCM are rejected.
- `data/events.csv` stores UUID, UTC timestamp, source, audio offset, RMS,
  baseline, threshold, category, vote share, nearest distance, priority and
  outcome. Monitoring audio stays only in bounded memory and is never saved or
  transmitted. **Explicit training imports do save the WAV copies you select.**
- CSV quotes commas/quotes/newlines and uses UTF-8; writes are forced to disk.
  A process lock prevents concurrent sessions writing the same file. Malformed
  history is left untouched. GUI clearly switches to memory-only mode if the CSV
  cannot open; write failures are prominently reported. Back up/repair the file
  or choose another `--data` directory, rather than silently discarding history.
- GUI reloads the last 1,000 events; the CSV retains all records. Statistics
  describe that retained window, not lifetime totals. Export/view CSV in a text
  editor; do not execute spreadsheet formulas from untrusted source filenames.
- Stop/window-close release audio streams/lines and cancel work. UI mutations
  happen on Swing's EDT; waveform updates are coalesced rather than flooding it.

## Testing and verification

```sh
java Build.java test
java Build.java ui-test      # optional: needs a real graphical desktop
```

`test` covers calibration, rolling eviction, adaptive floor/cooldown boundaries,
loudest-window padding, PCM decoding/resampling/anti-aliasing, malformed/truncated
WAVs, finite MFCCs/spectral features, exact k-NN matches and coverage validation,
staged training imports/deduplication/conflicting labels, alert priorities/gates,
CSV round trips/locking/corruption, capture timing/EOF, missing-model behavior,
blocking-source stop, actual WAV→features→k-NN→CSV integration and Swing rendering
on the EDT. No third-party test framework is used.

`ui-test` displays an actual Swing window, loads mechanical test fixtures, analyzes
a WAV, classifies an unseen mechanical recording, checks error recovery, priority
alerts, keyboard tab navigation, event-table updates and persisted history/lock
release. It saves four component renders to `build/ui-tab-0.png` through
`build/ui-tab-3.png`; it does
not capture other desktop windows or require a screen-sharing permission portal.
Test tones/feature vectors are temporary **algorithm-mechanics fixtures only**:
they are not real acoustic training data, are not bundled with the application,
and do not measure classification accuracy.

Verified here with OpenJDK 21 on Linux: warning-free compilation, **208 regression
checks**, real-display Swing smoke test and an actual five-second Java Sound input
test. The input test detected clipping; lower OS input gain and rehearse on the
intended laptop. Real-world recognition still requires a labeled evaluation:
check OS input/permissions, calibrate quietly, play safe licensed test recordings,
verify Stop/restart and CSV reload. There is no claim of verified semantic
recognition without a real, held-out dataset.
See [TEST_RESULTS.md](TEST_RESULTS.md) for exact verification scope.

## Limitations

- Handcrafted features/k-NN do not reproduce pretrained YAMNet semantics or
  distress-vs-joyful-scream understanding. Similar acoustic events can be confused.
- Quiet threats may not trigger; loud startup events calibrate into the baseline;
  sustained loud sound raises the baseline. Mixed events inside one window get
  one label. Short/continuous sirens can be missed by the original energy gate.
- Microphone sensitivity, room acoustics, distance and OS gain vary. Settings
  need real-world tuning; normalized RMS is **not calibrated dB SPL**.
- No trained dataset is supplied and no real-world accuracy has been evaluated.
  One example per class or a high vote share does not establish reliability.
- No Pi/GSM, remote dispatch, simulated fleet/map, WebSocket server, deep neural
  inference or synthetic analytics is included. All requested laptop functions
  work locally; no physical/network alert is sent.
