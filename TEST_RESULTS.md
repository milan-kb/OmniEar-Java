# Verification results

Verified on Linux using Eclipse Temurin JDK `21.0.12.1+1`.

| Check | Result |
|---|---|
| Java compilation (`--release 21 -Xlint:all -Werror`) | PASS, no warnings |
| `java Build.java test` | PASS: 200 checks |
| `java Build.java ui-test` | PASS on the real graphical desktop |
| `java Build.java jar` | PASS; `build/OmniEar.jar` created |
| `java -jar build/OmniEar.jar --help` | PASS; executable entry point |
| `jdeps -s build/OmniEar.jar` | Only `java.base` and `java.desktop` |
| Upstream `git status --porcelain` | Empty; original checkout unchanged |

The regression suite includes headless command-line training, explicit clip
classification, gated WAV processing, missing-model refusal, CSV persistence,
PCM8/16/24/32 and float32/64 decoding, 8/16/44.1/48/96 kHz input, anti-aliasing,
downmixing, malformed/truncated audio, DSP, k-NN, detector boundaries and cleanup.
The 44.1 kHz duration test exposed a float-precision rate-ratio error; converting
the ratio in double precision fixed it and the regression now passes.

A deliberate review mutation changed the strict energy comparison from `>` to
`>=`. The threshold-equality test rejected it. The original comparison was
restored and the full suite then passed. This is a test-effectiveness check,
**not a classification evaluation metric**.

The real-display smoke test loaded temporary mechanical fixtures, analyzed a
WAV through the GUI, verified the detected-event table, closed the window and
reopened the persisted CSV after its lock was released. A render of the actual
Swing components is available at `build/ui-smoke.png`. Desktop screen capture
was intentionally replaced with component painting: this host's Wayland
permission portal blocks unattended `Robot.createScreenCapture()`. The
application itself does not perform screen capture.

## Not verified / unresolved limits

- No real acoustic dataset was supplied or bundled. Synthetic signals in tests
  exercise algorithms and plumbing only; temporary training fixtures are deleted.
- No semantic classification accuracy, confidence calibration or real-world
  safety performance has been evaluated. Java k-NN does not reproduce YAMNet.
- Physical microphone capture quality, gain and room-dependent calibration need
  a manual laptop check. Automated tests do not open/record the microphone.
- GUI audio and CSV error handling are implemented; OS/audio-device behavior
  outside this Linux/JDK environment has not been manually verified.
