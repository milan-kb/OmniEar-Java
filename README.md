# OmniEar — Java OOP semester project

An offline **Java 21** desktop application for exploring an acoustic-monitoring
pipeline: read microphone/PCM WAV input, detect energy anomalies, classify audio
into five sound classes, and display/persist local event metadata. Built to make
OOP, DSP, threading and file I/O explainable in a CSE semester project. Swing and
Java Sound are standard JDK components; no third-party runtime/build libraries.

**Direct WAV classification works with a bundled real k-NN model. Continuous
live detection remains unreliable:** it misses events and can raise false alerts.
This is an academic prototype, not an emergency-response system. Vote share is
neighbor agreement, not emergency probability. No sound playback, SMS, remote
dispatch, Pi/GSM hardware, deep neural inference or network server is included.

## START HERE — for a new teammate

1. **[Getting started](docs/GETTING_STARTED.md)** — clone, JDK 21, Windows/macOS/Linux setup, first launch and troubleshooting.
2. **[Team handoff](docs/TEAM_HANDOFF.md)** — what works, known limits, code-reading route and safe first contributions.
3. **[Five-minute demo](docs/DEMO.md)** — show all five classes and genuine failures honestly.
4. **[Architecture](docs/ARCHITECTURE.md)** → **[UML](docs/UML.md)** — trace audio, predictions, alerts and threads.
5. **[Testing](docs/TESTING.md)** → **[Contributing](CONTRIBUTING.md)** — check a change and submit a reviewed GitHub PR.

## Features you can demonstrate

- Four Swing tabs: **Dashboard**, **WAV Testing**, **Training**, **Alert History**;
  readiness/mode warnings, real waveforms, scores and local P0/P1/P4 priorities.
- **Classify WAV / Classify example:** loudest 2.5 s → Java FFT/MFCC features →
  k-NN; bypasses the continuous scheduler. Ten preserved licensed examples now
  give seven direct-label matches and three known mismatches, not an accuracy estimate.
- **Start monitoring / Analyze WAV:** same bounded onset/periodic window pipeline,
  active from startup, with explicit missed-event limitations. No playback.
- Five-second microphone input diagnostic: RMS/dBFS, peak, near-full-scale
  percentage and OS gain guidance; no audio/events saved by that diagnostic.
- Bundled **217 real training WAVs**; explicit validated imports/reload, duplicate
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

Both Java versions should be **21**; tests end with **`PASS: 560 checks`**.
Wait for **Classification ready · 217 WAVs / 5 classes** in a fresh data directory.
Open **WAV Testing → Example label → Classify example**. Labels/credits are
shown as provenance; actual inference determines prediction and match/mismatch.

Startup installs the bundle only into an **absent** default training directory.
It preserves existing/custom training, including old 170-file installations.
The new evidence gates were evaluated with the 217-file bundle, not custom models.
Use a new `--data` name for a fresh demo;
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

Try a genuine example after closing the GUI:

```sh
java -jar build/OmniEar.jar --classify datasets/held-out/explosion/fsd-256296.wav --data data/first-prediction
java -cp build/OmniEar.jar omniear.SubmissionDemoTest
```

With fresh bundled training the first predicts explosion but is **distance
suppressed**, vote about 0.616, distance about 0.411. Correct identification does
not guarantee an alert. The second checks all ten preserved demos, including
failures; raw replay now emits decisions, but only two demos route alerts. Follow
[Testing](docs/TESTING.md) for `--analyze`, `--evaluate`, `--mic-test`, CSV checks
and result interpretation; see [Setup](docs/GETTING_STARTED.md) for JDK help.

## Evidence, provenance and further reading

| Document | Use it for |
|---|---|
| [Project report](docs/PROJECT_REPORT.md) / [feature audit](docs/FEATURE_AUDIT.md) | Academic narrative and control-to-code claim checks. |
| [Verification results](TEST_RESULTS.md) | Dated tests, platform scope, behavior comparisons and historical acoustic results. |
| [Datasets](DATASETS.md) | Real-recording selection, split limits, licensing and rationale. |
| [Attribution](datasets/ATTRIBUTION.md), [manifest](datasets/manifest.json), [dataset license](datasets/LICENSE-DATASET) | Per-recording authors, sources, licenses and hashes; preserve when sharing assets. |
| [Demo catalog](demo/samples.tsv) / [actual results](demo/VERIFIED_RESULTS.tsv) | Ten separately packaged, already-inspected demonstration files. |
| [Upstream inspection](INSPECTION.md) | What the Java implementation preserves/replaces; upstream repository is untouched. |
| [Evaluation guide](evaluation/README.md) / [rejection decision](evaluation/REJECTION_DECISION.md) | Frozen historical experiments; rejected candidates were not promoted. |
| [Acoustic repair decision](evaluation/ACOUSTIC_DECISION.md) / [replacement path](evaluation/ACOUSTIC_REPLACEMENT.md) | Current development/fresh-final results, conservative routing tradeoffs and unresolved recognition failures. |

Historical original direct result: **36/50** on an inspected subset; later final
direct result: **25/48**, with raw routed recall **1/18**. These are different
curated sets, not live reliability. Current 42-file source-disjoint final:
direct labels **23/42 → 25/42**, raw routed targets **2/18 → 7/18**, raw false
alerts/hour **48.23 → 12.06** over only 298.59 s of background. Continuous routed
recall **falls 9/18 → 2/18** as false alerts fall 11 → 2. Reliable recognition
is **not achieved**, particularly screams/explosions; new final has no siren or
explosion positives. All existing test sets are now inspected and need
replacement before new tuning/final evaluation. Verification so far is on
Linux/JDK 21; Windows/macOS, assistive technology and long-duration microphone
behavior require testing on the actual machine.
