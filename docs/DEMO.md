# Five-minute demonstration and viva

[START HERE](../README.md) · [First-time setup](GETTING_STARTED.md) · [Tests](TESTING.md) · [Team handoff](TEAM_HANDOFF.md)

## Rehearsal / submission setup

Inside a fresh checkout of `OmniEar-Java`, with a full JDK 21:

```sh
java Build.java test
java Build.java jar
java -jar build/OmniEar.jar --data data/demo-session
```

The first launch installs 217 bundled training WAVs into a **missing**
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
`evaluation/acoustic-candidate-demo.tsv`; `demo/VERIFIED_RESULTS.tsv` preserves
old-model observations. These are selected **already-inspected** examples,
not an independent accuracy test.

## Timed walkthrough

| Time | Action and explanation |
|---|---|
| 0:00–0:35 | Show readiness: 217 WAVs / five classes. “Ready means the model can run, not that it is reliable.” Training counts are 40/22/40/33/82; never import demos into training. |
| 0:35–1:20 | WAV Testing (Alt+2): scream `fsd-264918.wav`, **Classify example** (Alt+D). Label match, vote ≈.774, distance ≈.960, but background-conflict suppressed. This bypasses scheduling; it is not microphone detection or playback. |
| 1:20–2:00 | Wind `fsd-381853.wav`: still wrongly labeled scream, vote ≈.733, but no longer a routed alert. Show **LABEL MISMATCH** and suppression. Labels are provenance, never classifier input. |
| 2:00–2:35 | Complete all classes: explosion 256296 and impact 320167 match but are suppressed; siren 238228 matches and alerts P4; background 110612 is suppressed. Votes are agreement, not certainty. |
| 2:35–3:15 | **Analyze WAV…**: siren `datasets/held-out/siren_traffic/fsd-238228.wav` now routes one alert with duplicate suppression. Explosion 256296 emits decisions but still no alert. Use executable demo output for a JAR-only fallback. |
| 3:15–4:00 | **Test microphone · 5 s**: speak normally; show RMS/dBFS, peak and near-full-scale percentage. Adjust OS gain if needed. This proves input operation, not recognition. Monitoring is active from startup with up to 2.5 s context; no quiet warmup requirement. |
| 4:00–4:30 | Alert History shows routed alerts; Dashboard also includes rejected/duplicate decisions. Close and reopen the same data directory to demonstrate persistence. |
| 4:30–5:00 | Show UML and explain the `AudioSource` interface, shared `PcmSource`, and SwingWorker/audio executor versus EDT. Conclude with limitations and preserved evaluation reports. |

No explosives, breakage or distress enactment is needed for the presentation.
The GUI does not play audio; any external playback is a separate action and must
not be described as an application feature or a validated microphone test.

## Verified examples (bundled model)

These preserved examples now give seven direct-label matches. Historical catalog
observations remain marked as historical; actual fresh-bundle results are:

| Dataset category | First example → prediction/status | Second example → prediction/status |
|---|---|---|
| Scream | 264918 → scream / background-conflict suppressed | 249745 → siren / distance suppressed |
| Explosion | 256296 → explosion / distance suppressed | 426167 → explosion / distance suppressed |
| Impact/crash | 320167 → impact / background-conflict suppressed | 367624 → explosion / distance suppressed |
| Traffic siren | 238228 → siren / P4 alert | 378257 → siren / background-conflict suppressed |
| Background | 110612 → background / suppressed | 381853 → scream / background-conflict suppressed |

All ten raw replays emit classifications; only scream 264918 and siren 238228
route an alert with the evaluated fresh 217-file bundle.
Do not silently add quiet audio, change gains or tune controls to make the demo
appear more successful. Scream examples are acted; impacts are glass breakages,
not vehicle collisions. Seven matches out of a deliberately chosen ten is not an
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

Expect the matching categories above, one routed siren alert and four suppressed
events in a fresh CSV. A sixth command exposes the real label failure:

```sh
java -jar build/OmniEar.jar --classify datasets/held-out/background/fsd-381853.wav --data data/five-class-demo
```

That wind recording predicts scream but is background-conflict suppressed; do not
relabel it to hide the error. The GUI supplies label comparison/credits; CLI prints actual inference
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
  continuous replay uses onset-tail and periodic windows, with no warmup veto.
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
- **Why can direct and replay disagree?** Replay analyzes rolling windows and
  suppresses duplicate episodes; direct mode selects one loudest window. Context
  changes features. `NO_EVENT` is no emitted classification, not background;
  silence skips inference, and conservative gates miss targets.
- **How are files protected?** `EventStore` owns an exclusive CSV lock, validates
  history and forces appends. Invalid history is preserved; imports validate
  copies rather than altering original recordings. See [Architecture](ARCHITECTURE.md).
