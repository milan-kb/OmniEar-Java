# Testing and interpreting results

## Classroom Demo checks

Current `java Build.java test` and packaged `omniear.TestSuite` pass **308 checks**
(261 original + 47 classroom checks). The new headless `ClassroomDemoTest` uses
five genuine bundled recordings, checks two complete runs, real alert/suppression,
CSV reopen, reset and startup isolation. No model or existing alert logic changed.

After `java Build.java jar`, run:

```sh
java -cp build/OmniEar.jar omniear.ClassroomDemoTest
java -cp build/OmniEar.jar omniear.ClassroomUiTest
```

The second opens the actual `--demo` entry point and plays all five WAVs through
Java Sound; it requires a graphical desktop and audio output, **not a microphone**.
It verifies native reset/keyboard controls, real outcomes, stop/replay, close,
CSV persistence and fresh restart. A missing output fails this check rather than
silently skipping playback. See [DEMO.md](DEMO.md) for silent presentation recovery.
The earlier cleanup counts below remain historical where explicitly dated.

[START HERE](../README.md) · [Setup](GETTING_STARTED.md) · [Demo](DEMO.md) · [Evidence](../TEST_RESULTS.md)

Run commands from the repository root with JDK 21. Build helper actions compile
both source and tests with warnings treated as errors. No test framework or new
dependency is required. Exit code 0 means successful command completion;
`Main` exits 2 on invalid action/input or a reported failure. Java tests throw
on failed assertions. Check exit status with `$?` in bash/zsh or `$LASTEXITCODE`
in PowerShell immediately after the command; do not dismiss a failure as "just UI".

## Required checks for an ordinary contribution

```sh
java Build.java test
java Build.java jar
java -jar build/OmniEar.jar --help
java -cp build/OmniEar.jar omniear.SubmissionDemoTest
```

- **`test` → `PASS: 261 checks`** at the integrated cleanup baseline: all 249
  original checks plus twelve traversal/EDT-polling checks. Future intentional
  test additions can raise this count. Tests include PCM conversions, detector
  boundaries, FFT/MFCC/k-NN, imports, CSV corruption/locking, EOF/Stop, workers,
  CLI startup/restart and actual licensed demo outcomes. Temporary mechanical
  tones/vectors exercise plumbing, **not acoustic accuracy**.
- **`jar`** creates `build/OmniEar.jar`; silence can mean success.
- **`--help`** lists supported GUI/path/CLI options.
- **`SubmissionDemoTest`** prints ten TSV rows plus a pass message. It loads an
  isolated temporary bundled model, checks actual successes **and failures**,
  replays raw clips, and cleans up. It does not append to your session CSV.
  Compare with [`demo/VERIFIED_RESULTS.tsv`](../demo/VERIFIED_RESULTS.tsv).

Optional packaged full-suite check:

```sh
java -ea -cp build/OmniEar.jar omniear.TestSuite
```

This now ends at 308 checks and verifies packaged resources. Run it from the
repository root because some tests use repository WAV paths. `SubmissionDemoTest`
alone can also run from another directory when given an absolute JAR path.

## Real-display Swing checks

```sh
java Build.java ui-test
java -cp build/classes omniear.TrainedSmokeTest --ui
```

Expect `PASS: real-display Swing smoke test...` and
`PASS: trained fresh-start smoke test...`. These checks open real windows; leave
the desktop available while Robot clicks/types. The first covers four tabs,
error recovery, Alt+2 navigation, CSV persistence/lock release and untrained
controls; renders go to `build/ui-tab-0.png` through `build/ui-tab-3.png`.
The second loads 170 real training files, predicts the real development scream,
clicks the guided wind example and checks the visible false scream/P0 mismatch.
It writes `build/submission-demo.png`. Neither test opens the microphone.

For packaged Swing checks, after `jar`, replace `build/classes` with
`build/OmniEar.jar` and use `omniear.UiSmokeTest` / `omniear.TrainedSmokeTest --ui`.
On KDE/Wayland, synthetic focus can be denied. Do not remove focus assertions or
increase waits to hide a failure. Click/focus the window and investigate; the
existing isolated rootful Xwayland procedure is in [verification evidence](../TEST_RESULTS.md).
It is an optional host test facility, not an application dependency.

## Real WAV: direct versus gated replay

Close any GUI using the same data folder. These use a dedicated ignored session:

```sh
java -jar build/OmniEar.jar --classify datasets/held-out/explosion/fsd-256296.wav --data data/testing-session
java -jar build/OmniEar.jar --analyze datasets/held-out/explosion/fsd-256296.wav --data data/testing-session
```

With fresh bundled training: direct mode reports explosion/P0/ALERT, vote about
0.764 and distance about 0.478. Raw analysis completes with **no emitted event**
under default calibration. This is expected, not a successful background prediction.
All ten raw demos emit `NO_EVENT`; see [Demo](DEMO.md) for every class and known
failure. Direct classification produces CSV events, including suppressed results.
Gated analysis writes only emitted events; no trigger means no event row.

To reproduce the original inspected 50-file evaluation:

```sh
java -jar build/OmniEar.jar --evaluate datasets/held-out --data data/testing-session
```

Expect 50 evaluated, 36 direct labels correct, zero skipped with unchanged
bundled training. The report separates direct and raw replay matrices, routing,
false positives, and a **three-second digital-silence** diagnostic. That diagnostic
is artificial context, not live detection or new training data. Rows are true
labels; columns are predictions. Recall describes missed labeled examples;
precision describes how many predictions of a class were right. `N/A` means
no denominator, not perfect performance. Existing evaluation sets are inspected;
do not use them as untouched tests for future tuning.

Advanced behavior comparison, not a starter task:

```sh
java -Xmx512m -cp build/OmniEar.jar scripts/ReplayEvaluation.java datasets/training rejection-dev
java -Xmx512m -cp build/OmniEar.jar scripts/ReplayEvaluation.java datasets/training rejection-final
```

These historical multi-scenario checks do not change runtime training. Cleanup
verification reproduced 1,370 development and 671 final non-header rows exactly.
For replay/metric definitions and frozen evidence see [evaluation/README.md](../evaluation/README.md).

## CSV persistence check

If `data/csv-check` already contains someone else's work, substitute a new name.
Do not run another instance on this CSV while checking:

```sh
java -jar build/OmniEar.jar --classify datasets/held-out/explosion/fsd-256296.wav --data data/csv-check
java -jar build/OmniEar.jar --classify datasets/held-out/explosion/fsd-256296.wav --data data/csv-check
```

View the file in a text editor, or:

```sh
# Linux/macOS:
cat data/csv-check/events.csv
```

```powershell
# Windows PowerShell:
Get-Content data/csv-check/events.csv
```

In a fresh session expect a header and **two records**. Each has
`label=explosion`, `priority=P0`, `status=ALERT`; `source` includes
`[clip; Stage 1 bypass]`, `audio_seconds=0.0`, `baseline=0.0`, `threshold=0.0`.
Votes/distances match; UUID/timestamp differ naturally. Reopen the GUI with
`java -jar build/OmniEar.jar --data data/csv-check`: Alert History should reload
both alerts. Close normally, then repeat a CLI command to check lock release.
Do not use line counts for arbitrary filenames containing newlines: CSV supports
quoted multiline fields. Avoid spreadsheet formula execution from untrusted filenames.

## Microphone diagnostic (manual, separate from tests)

```sh
java -jar build/OmniEar.jar --mic-test
```

Speak normally for five seconds. Expect `Microphone test complete` plus RMS,
dBFS, peak and near-full-scale percentage, then automatic release. It saves
**no audio or events**, does not use the classifier, and is not a recognition test.
Values depend on hardware/current gain; do not expect a particular numeric result.
GUI equivalent: Dashboard → **Test microphone · 5 s**; Stop cancels it.

- Windows: Settings → Privacy & security → Microphone; permit desktop apps,
  then check System → Sound → Input device/volume.
- macOS: System Settings → Privacy & Security → Microphone; permit the app or
  terminal launching Java, then check Sound → Input.
- Linux: desktop sound settings → correct default recording input, mute and
  gain; close competing capture apps. A loopback can be the OS default.
- Very quiet: check source/mute/gain. Near-full-scale samples: lower OS gain
  and retest. Decoded dBFS is not calibrated dB SPL; absence of near-full-scale
  samples cannot rule out analog clipping or channel cancellation.

Do not perform dangerous events or promise alerts. If hardware/permissions are
unavailable, report the diagnostic as **not verified** and use licensed WAV demos.

## Latest verification scope

Integrated cleanup: **261 checks**, JAR/demo/help, Swing suites on isolated
Xwayland, exact before/after demo/replay comparisons, portable fresh startup,
CSV persistence and cleanup passed on Linux/OpenJDK 21. Documentation commands
are checked on this host where applicable; Windows/macOS installers and runtime
behavior need teammate verification. Dated acoustic results and device observations
are in [TEST_RESULTS.md](../TEST_RESULTS.md), not a promise of live reliability.

**Onboarding rehearsal, 2026-10-14:** a fresh local clone of `5f0e94d` passed
build, 261 normal and 261 packaged checks, all four source/JAR Swing smoke
commands, JAR/help/run-flag dispatch, training inspection, portable ten-demo
execution, the six exact five-class/failure CLI commands, raw no-event analysis,
36/50 historical evaluation and two-record CSV restart equality. Documentation
links/anchors were checked. Runtime/build/test/data files are unchanged from main.

The five-second OS-default input diagnostic also captured/released successfully,
but reported **RMS 0.6871 (-3.3 dBFS), peak 1.0000, near full scale 0.39%**:
**possible clipping — lower OS input gain and retest before a presentation**.
This is a dated decoded-input observation, not identification of the physical
source, proof of analog clipping or evidence of recognition. No audio was saved.

If a check fails: keep its output, OS/JDK versions, command and current commit;
reproduce before changing code. See [Contributing](../CONTRIBUTING.md) and
[setup troubleshooting](GETTING_STARTED.md#troubleshooting). Do not skip assertions,
discard CSV/training or alter demo expectations just to get green.
