# Verification results

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
