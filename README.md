# OmniEar — Java OOP semester project

An offline **Java 21** desktop application for exploring an acoustic-monitoring
pipeline: read microphone/PCM WAV input, detect energy anomalies, classify audio
into five sound classes, and display/persist local event metadata. Built to make
OOP, DSP, threading and file I/O explainable in a CSE semester project. Swing and
Java Sound are standard JDK components; no third-party runtime/build libraries.

**Direct WAV classification works with a bundled real k-NN model. Continuous
live detection remains unreliable:** it misses events and can raise false alerts.
This is an academic prototype, not an emergency-response system. Vote share is
neighbor agreement, not emergency probability. No SMS, remote
dispatch, Pi/GSM hardware, deep neural inference or network server is included.

**For the five-minute OOP presentation:** launch `java -jar build/OmniEar.jar --demo`.
The isolated **Classroom Demo** plays five genuine bundled WAVs and runs the
unchanged real classifier/alert policy, with waveform, severity, CSV-backed
history and **Reset Demo**. No microphone setup or external dataset is needed.
Follow [the exact demo guide](docs/DEMO.md). Selected successes are not general accuracy.

## START HERE — for a new teammate

1. **[Getting started](docs/GETTING_STARTED.md)** — clone, JDK 21, Windows/macOS/Linux setup, first launch and troubleshooting.
2. **[Team handoff](docs/TEAM_HANDOFF.md)** — what works, known limits, code-reading route and safe first contributions.
3. **[Five-minute demo](docs/DEMO.md)** — reproducible five-class playback/classification, recovery and honest limitations.
4. **[Architecture](docs/ARCHITECTURE.md)** → **[UML](docs/UML.md)** — trace audio, predictions, alerts and threads.
5. **[Testing](docs/TESTING.md)** → **[Contributing](CONTRIBUTING.md)** — check a change and submit a reviewed GitHub PR.

## Features you can demonstrate

- Four Swing tabs: **Dashboard**, **WAV Testing**, **Training**, **Alert History**;
  readiness/mode warnings, real waveforms, scores and local P0/P1/P4 priorities.
- **Classroom Demo… / `--demo`:** one-click playback and real classification of
  five curated successes, isolated training/CSV, repeatable results and non-destructive reset.
- **Classify WAV / Classify example:** loudest 2.5 s → Java FFT/MFCC features →
  k-NN; bypasses the continuous detector. Ten licensed examples include one
  direct-label match and one known mismatch for each class.
- **Start monitoring / Analyze WAV:** same RMS gate, calibration and capture
  pipeline, with explicit missed-event limitations. WAV analysis has no playback.
- Five-second microphone input diagnostic: RMS/dBFS, peak, near-full-scale
  percentage and OS gain guidance; no audio/events saved by that diagnostic.
- Bundled **170 real training WAVs**; explicit validated imports/reload, duplicate
  and conflicting-label checks, and labeled-folder evaluation.
- Durable quoted UTF-8 CSV, reopen/history, process locking, error recovery and
  explicit memory-only warnings. Monitoring audio is never saved or transmitted;
  explicit training imports do save the WAV copies you select.

Classes: `scream_distress`, `explosion`, `impact_crash`, `siren_traffic`, `background`.
Labels are narrow: most screams are acted and impacts are mainly glass/ceramic
breakages, not proof of distress or vehicle crashes. Background never routes an alert.

## Prerequisites and quick start

Install **Git** and a full **JDK 21** (not just a JRE). GUI needs a graphical desktop;
microphone use needs a working OS-default recording input/permission. Headless
tests and WAV commands need no microphone. No Maven/Gradle/Python/Node is required.

The following works in PowerShell and bash/zsh; run commands one at a time:

```sh
git clone https://github.com/milan-kb/OmniEar-Java.git
cd OmniEar-Java
java -version
javac -version
java Build.java test
java Build.java jar
java -jar build/OmniEar.jar --data data/team-demo
```

Both Java versions should be **21**; tests end with **`PASS: 308 checks`**
(261 at the cleanup baseline). Wait for **Classification ready · 170 WAVs / 5 classes**.
Open **WAV Testing → Example label → Classify example**. Labels/credits are
shown as provenance; actual inference determines prediction and match/mismatch.

Startup installs the bundle only into an **absent** default training directory.
It preserves existing/custom training. Use a new `--data` name for a fresh demo;
never delete someone else's data to reset readiness. Offline runtime files are
relative to your current folder. Close a session before reusing its CSV.

## Build and CLI entry points

| Command | Purpose |
|---|---|
| `java Build.java build` | Compile source/tests and copy resources to `build/classes`. |
| `java Build.java test` | Regression/integration checks; no accuracy claim. |
| `java Build.java run` | Build and launch Swing; accepts application flags afterward. |
| `java Build.java ui-test` | Real-display Swing/Robot checks; needs desktop focus. |
| `java Build.java jar` | Create portable `build/OmniEar.jar`. |
| `java -jar build/OmniEar.jar --help` | List supported paths/actions. |
| `java -jar build/OmniEar.jar --demo` | Isolated classroom playback/classification; no microphone. |

Try a genuine example after closing the GUI:

```sh
java -jar build/OmniEar.jar --classify datasets/held-out/explosion/fsd-256296.wav --data data/first-prediction
java -cp build/OmniEar.jar omniear.SubmissionDemoTest
```

With the unchanged bundled model the first predicts explosion/P0/ALERT, vote
about 0.764, distance about 0.478. The second checks all ten packaged demos,
including failures; all ten raw gated replays emit `NO_EVENT`. Follow
[Testing](docs/TESTING.md) for `--analyze`, `--evaluate`, `--mic-test`, CSV checks
and result interpretation; see [Setup](docs/GETTING_STARTED.md) for JDK help.

## Evidence, provenance and further reading

| Document | Use it for |
|---|---|
| [Project report](docs/PROJECT_REPORT.md) / [feature audit](docs/FEATURE_AUDIT.md) | Academic narrative and control-to-code claim checks. |
| [Verification results](TEST_RESULTS.md) | Dated tests, platform scope, behavior comparisons and historical acoustic results. |
| [Datasets](DATASETS.md) | Real-recording selection, split limits, licensing and rationale. |
| [Attribution](datasets/ATTRIBUTION.md), [manifest](datasets/manifest.json), [dataset license](datasets/LICENSE-DATASET) | Per-recording authors, sources, licenses and hashes; preserve when sharing assets. |
| [Demo catalog](demo/samples.tsv) / [actual results](demo/VERIFIED_RESULTS.tsv) / [classroom runs](demo/CLASSROOM_RESULTS.tsv) | Ten separately packaged, already-inspected demonstration files; five rehearsal repeat outcomes. |
| [Upstream inspection](INSPECTION.md) | What the Java implementation preserves/replaces; upstream repository is untouched. |
| [Evaluation guide](evaluation/README.md) / [rejection decision](evaluation/REJECTION_DECISION.md) | Frozen historical experiments; rejected candidates were not promoted. |

Historical original direct result: **36/50** on an inspected subset; later final
direct result: **25/48**, with raw routed recall **1/18**. These are different
curated sets, not live reliability. The five selected matches among ten demos
are not an accuracy estimate. All existing test sets are inspected and need
replacement before new tuning/final evaluation. Verification so far is on
Linux/JDK 21; Windows/macOS, assistive technology and long-duration microphone
behavior require testing on the actual machine.
