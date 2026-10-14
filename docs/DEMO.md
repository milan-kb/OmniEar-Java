# Five-minute demonstration and viva

[START HERE](../README.md) · [First-time setup](GETTING_STARTED.md) · [Tests](TESTING.md) · [Team handoff](TEAM_HANDOFF.md)

## Rehearsal / submission setup

Inside a fresh checkout of `OmniEar-Java`, with a full JDK 21:

```sh
java Build.java test
java Build.java jar
java -jar build/OmniEar.jar --data data/demo-session
```

The first launch installs 170 bundled training WAVs into a **missing**
`data/demo-session/training`; restart reuses them and reloads `data/demo-session/events.csv`.
Use a new directory for a clean rehearsal rather than deleting anyone's data.
Custom existing training folders are preserved and can produce different results.
No network, Python, extra libraries or audio download is required. A portable
copy of the JAR also includes all ten demonstration WAVs and attribution.

Optional preflight (do not open two instances on the same CSV):

```sh
java -cp build/OmniEar.jar omniear.SubmissionDemoTest
java -jar build/OmniEar.jar --mic-test
```

The first command installs an isolated temporary model, computes direct and raw
pipeline results, checks the observed demo outcomes, then deletes its temporary
files. It does not save events to your demonstration CSV. Output is recorded in
`demo/VERIFIED_RESULTS.tsv`. These are selected **already-inspected** examples,
not an independent accuracy test.

## Timed walkthrough

| Time | Action and explanation |
|---|---|
| 0:00–0:35 | Show Dashboard readiness: 170 WAVs / five classes. “Ready means the model can run, not that it is reliable.” Open Training to show 40/18/40/32/40 counts; do not import demo audio into training. |
| 0:35–1:20 | Open WAV Testing (Alt+2, or click). Select scream `fsd-264918.wav`, press **Classify example** (Alt+D). Show the actual label match, P0, vote ≈0.716, distance ≈0.981 and source/license. Explain that classification bypasses calibration and neither uses the microphone nor plays audio. |
| 1:20–2:00 | Choose background `fsd-381853.wav` (wind), classify. It actually predicts scream/P0, vote ≈0.562. Show **LABEL MISMATCH**, not a hidden “success.” The dataset label is context; it is not supplied to the classifier. |
| 2:00–2:35 | Complete all five classes: select matching explosion 256296, impact 320167, siren 238228 and background 110612, clicking **Classify example** after each. Show P0/P1/P4 or suppression; scores are neighbor agreement, not certainty. Scroll/drag the divider for provenance/results. |
| 2:35–3:15 | Click **Analyze WAV…**, select `datasets/held-out/explosion/fsd-256296.wav`. It completes with no event under default raw calibration, although direct classification identifies explosion. Explain warmup, capture and quiet/sustained-event failures. In a JAR-only presentation, use the raw replay column from the executable demo check instead. |
| 3:15–4:00 | Dashboard → **Test microphone · 5 s**. Speak normally; show RMS/dBFS, peak, near-full-scale percentage. Adjust input/mute/gain in OS settings and rerun if needed. This proves input operation, not threat recognition. If appropriate, Start monitoring, stay quiet for two seconds, show the live waveform, then Stop. Do not promise any alert. |
| 4:00–4:30 | Open Alert History; the actual false wind alert is visible too. Dashboard includes suppressed/untrained events. Close and reopen using the same `--data data/demo-session` to demonstrate CSV persistence. |
| 4:30–5:00 | Show UML and explain the `AudioSource` interface, shared `PcmSource`, and SwingWorker/audio executor versus EDT. Conclude with limitations and preserved evaluation reports. |

No explosives, breakage or distress enactment is needed for the presentation.
The GUI does not play audio; any external playback is a separate action and must
not be described as an application feature or a validated microphone test.

## Verified examples (bundled model)

Each category has a real direct-label match and a real mismatch:

| Dataset category | Match: source ID → prediction | Failure: source ID → actual prediction/status |
|---|---|---|
| Scream | 264918 → scream / P0 | 249745 → siren / low-vote suppressed |
| Explosion | 256296 → explosion / P0 | 426167 → background / suppressed |
| Impact/crash | 320167 → impact / P1 | 367624 → explosion / low-vote suppressed |
| Traffic siren | 238228 → siren / P4 | 378257 → background / suppressed |
| Background | 110612 → background / suppressed | 381853 → scream / P0 false alert |

All ten original raw WAV replays emit **no event** with a fresh default pipeline.
Do not silently add quiet audio, change gains or tune controls to make the demo
appear more successful. Scream examples are acted; impacts are glass breakages,
not vehicle collisions. Five matches out of a deliberately chosen ten is not an
accuracy claim. Full licenses, authors and original metadata are in
the [demo catalog](../demo/samples.tsv), [attribution](../datasets/ATTRIBUTION.md)
and [manifest](../datasets/manifest.json).

## Copy-paste five-class CLI fallback

After building the JAR, close any GUI sharing this session. Run from the
repository root; use a new data-folder name if this one contains existing work:

```sh
java -jar build/OmniEar.jar --classify datasets/held-out/scream_distress/fsd-264918.wav --data data/five-class-demo
java -jar build/OmniEar.jar --classify datasets/held-out/explosion/fsd-256296.wav --data data/five-class-demo
java -jar build/OmniEar.jar --classify datasets/held-out/impact_crash/fsd-320167.wav --data data/five-class-demo
java -jar build/OmniEar.jar --classify datasets/held-out/siren_traffic/fsd-238228.wav --data data/five-class-demo
java -jar build/OmniEar.jar --classify datasets/held-out/background/fsd-110612.wav --data data/five-class-demo
```

Expect the matching categories in the table above, four routed alerts and one
background-suppressed event in a fresh CSV. A sixth command exposes the real failure:

```sh
java -jar build/OmniEar.jar --classify datasets/held-out/background/fsd-381853.wav --data data/five-class-demo
```

That wind recording predicts scream/P0/ALERT; do not relabel it to hide the false
alert. The GUI supplies label comparison/credits; CLI prints actual inference
and saves metadata only. For a JAR-only demo without repository WAV paths, use
the bundled GUI selector or `omniear.SubmissionDemoTest`, not these filesystem paths.

## Recovery cues

- **Untrained:** supply usable WAVs for all five categories or relaunch with a
  new `--data` folder to install the bundle. Anomaly monitoring still works;
  direct classification/demo buttons are disabled. Existing folders are not
  overwritten automatically.
- **Microphone unavailable:** select the correct OS default recording device,
  grant permission, unmute, close competing apps, then retry. Use the WAV demo
  when hardware is unavailable; say explicitly that microphone capture was not
  demonstrated.
- **Very quiet / possible clipping:** speak normally during the five-second
  diagnostic; adjust OS input gain. dBFS is not dB SPL. Levels are measured after
  decoding/downmix/resampling; absence of near-full-scale samples cannot rule out
  upstream distortion, channel cancellation or hardware clipping.
- **CSV unavailable:** visible memory-only mode is not persistent success. Close
  any other instance; choose another data folder or repair/back up the file.
- **Keyboard focus:** click the window/tab before shortcuts. KDE/Wayland can
  deny synthetic Robot focus during automated tests; mouse controls still work.

## Likely viva questions

- **Why two WAV actions?** Direct inspection always extracts one clip window;
  continuous replay only classifies energy-triggered captures after warmup.
- **Where is polymorphism?** One `MonitorService` consumes either `AudioSource`;
  one pipeline uses the `SoundClassifier` interface. GUI and CLI use listeners.
- **Why not run everything on the EDT?** Audio reads, DSP and disk I/O can block.
  SwingWorker/executor jobs do that work; the EDT updates widgets.
- **What is encapsulated?** Private detector history/ring/channel state and
  defensively copied feature arrays; immutable prediction/event records.
- **What survives restart?** Training WAV copies and CSV event metadata. The
  classifier is rebuilt from recordings; raw microphone audio is never saved.
- **Why is the false alert important?** It shows actual inference and limits.
  Good mechanics and a few correct clips do not establish emergency reliability.
- **What do RMS, FFT and MFCC do?** RMS measures decoded signal energy; FFT
  gives a short-frame frequency spectrum; MFCCs compactly describe its shape.
  `AudioSamples` and `FeatureExtractor` implement them; none establishes intent.
- **What is k-NN "training" here?** `TrainingRepository` extracts WAV features
  and constructs `KnnClassifier` with training-only scales. It compares up to
  seven neighbors, not neural weights; WAVs persist, features are rebuilt.
- **Is a vote of 0.8 an 80% chance of danger?** No: it is a weighted share of
  neighbor votes. Alert gates are routing rules, not calibrated probabilities.
- **Why can direct and replay disagree?** Replay must pass warmup/RMS/capture
  before classification; direct mode skips those steps. `NO_EVENT` is no emitted
  classification, not background. Calibration can hide the actual event.
- **How are files protected?** `EventStore` owns an exclusive CSV lock, validates
  history and forces appends. Invalid history is preserved; imports validate
  copies rather than altering original recordings. See [Architecture](ARCHITECTURE.md).
