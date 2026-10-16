# OmniEar Demo Soundset — Testing Guide

This folder is a small, portable sound-testing package for the OmniEar Java OOP
presentation. It contains **ten real, licensed WAV recordings**:

- **`wav/`** — five recordings the bundled k-NN model classifies correctly.
  These drive the Classroom Demo.
- **`known-limitations/`** — five recordings that are **intentionally
  challenging**: the model misclassifies or suppresses them. They are included
  for honesty, not as failures to hide.

Every prediction is computed live by the Java app from the audio. No result is
hardcoded, and no file in this package is used as training data.

`manifest.tsv` lists, for every file: the ground-truth label, the expected
prediction, severity, alert behavior, the exact vote share and distance measured
with the bundled model, the author, the license and the SHA-256 checksum.

## Requirements

- **JDK 21** (a full JDK; the project targets standard Java only, no Maven or
  Python needed for the app).
- A graphical desktop for the Swing app. Missing a speaker is fine —
  classification does not need sound output.
- The OmniEar JAR (`OmniEar.jar`) built from the `feat/classroom-demo` branch.
  Build it once with:

  ```sh
  git clone --branch feat/classroom-demo https://github.com/milan-kb/OmniEar-Java.git
  cd OmniEar-Java
  java Build.java jar
  ```

## 1. Unzip

Extract `OmniEar-Demo-Soundset.zip`. You get:

```text
OmniEar-Demo-Soundset/
├── TESTING_GUIDE.md        ← this file
├── CHECKLIST.md            ← printable verification checklist
├── manifest.tsv            ← labels, licenses, expected results, hashes
├── wav/                    ← five correctly classified demo recordings
└── known-limitations/      ← five intentionally challenging recordings
```

## 2. Fastest path: Classroom Demo mode

Copy `OmniEar.jar` next to the extracted folder (or use the path to your built
JAR) and run:

```sh
java -jar OmniEar.jar --demo
```

Windows PowerShell works the same way. Wait for **Ready · choose recording 1 to
begin**, keep the speaker level low, then click **1** to **5** (or press
Alt+1…Alt+5). Each click plays the bundled recording, classifies it with the
real model and saves the actual result to an isolated CSV.

Expected outcomes, exactly as measured with this model:

| Button | Recording | Predicted category | Severity shown | Alert behavior |
|---|---|---|---|---|
| 1 · Scream | fsd-264918 | Distress scream | P0 · Critical | ALERT |
| 2 · Explosion | fsd-256296 | Explosion | P0 · Critical | ALERT |
| 3 · Impact | fsd-320167 | Impact / crash | P1 · High | ALERT |
| 4 · Siren | fsd-238228 | Traffic siren | P4 · Monitoring | ALERT |
| 5 · Background | fsd-110612 | Background noise | — | **No threat alert** (BACKGROUND_SUPPRESSED) |

Classroom Demo uses its own copy of the recordings and an isolated runtime data
folder, so your normal `data/events.csv` is never touched. **Reset Demo** starts
an empty history/new CSV and keeps all earlier files.

## 3. Classify any WAV yourself in the normal GUI

You can also test the extracted files by hand — including the challenging ones:

```sh
java -jar OmniEar.jar            # normal Dashboard (first launch installs the bundled model)
```

1. Open the **WAV Testing** tab.
2. Click **Classify WAV…** and choose a file from `wav/` or
   `known-limitations/`.
3. Read the result: predicted category, priority/severity, vote share and
   nearest distance.
4. Every classification is appended to `data/events.csv`.

This is **direct clip classification**: it skips the live-microphone calibration
stage, so it does not test microphone detection.

## 4. How to read the results

- **Predicted category** — the class chosen by the k-NN model.
- **Vote share** — weighted neighbor agreement, **not a probability** and not
  confidence. Values range up to 1.0.
- **Distance** — how far the clip's feature vector is from the nearest training
  example. Larger = less similar.
- **Priority / severity** — `P0` Critical (scream, explosion), `P1` High
  (impact/crash), `P4` Monitoring (siren). Background has no priority.
- **Status** — the existing alert policy's decision:

| Status | Meaning |
|---|---|
| `ALERT` | Passed the category's vote and distance gates; treated as a genuine event |
| `LOW_VOTE_SUPPRESSED` | Right shape, but neighbor agreement too low; no alert |
| `BACKGROUND_SUPPRESSED` | Classified as background or failed the background-ratio gate; no alert |

## 5. Expected results for every included file

Exact measured values (vote/distance from the committed classroom results):

| File | True label | Expected prediction | Expected status | Alert |
|---|---|---|---|---|
| `wav/1-scream__fsd-264918.wav` | scream | distress scream (0.716 / 0.981) | ALERT | yes |
| `wav/2-explosion__fsd-256296.wav` | explosion | explosion (0.764 / 0.478) | ALERT | yes |
| `wav/3-glass-break-impact__fsd-320167.wav` | impact | impact/crash (1.000 / 0.466) | ALERT | yes |
| `wav/4-traffic-siren__fsd-238228.wav` | siren | traffic siren (0.899 / 0.612) | ALERT | yes |
| `wav/5-background-rain__fsd-110612.wav` | background | background (0.825 / 0.391) | BACKGROUND_SUPPRESSED | **no** |
| `known-limitations/challenge-scream-boy__fsd-249745.wav` | scream | siren (0.637 / 1.395) | LOW_VOTE_SUPPRESSED | no |
| `known-limitations/challenge-explosion-distance__fsd-426167.wav` | explosion | background (0.500 / 1.058) | BACKGROUND_SUPPRESSED | no |
| `known-limitations/challenge-heavy-glass__fsd-367624.wav` | impact | explosion (0.629 / 0.566) | LOW_VOTE_SUPPRESSED | no |
| `known-limitations/challenge-siren-ambulance__fsd-378257.wav` | siren | background (0.683 / 0.409) | BACKGROUND_SUPPRESSED | no |
| `known-limitations/challenge-wind__fsd-381853.wav` | background | **scream (0.562 / 0.773)** | **ALERT (false)** | **yes — false alert** |

The numbers in parentheses are vote share / nearest distance to three decimals.
This selection demonstrates the pipeline honestly; it is **not** a general
accuracy estimate.

## 6. Command-line re-verification

Re-run any file through the packaged JAR (works headless):

```sh
java -jar OmniEar.jar --classify wav/1-scream__fsd-264918.wav --data /tmp/omniear-check
```

The last line matches the manifest, e.g.:

```text
… · scream_distress · P0 · votes 0.716 · distance 0.981 · ALERT
```

`--classify` explicitly bypasses the live calibration stage; it is the direct
clip path. To compare checksums on Linux/macOS: `shasum -a 256 <file>`,
on Windows: `certutil -hashfile <file> SHA256`.

## 7. About the known limitations

The five `known-limitations/` recordings are real, licensed events the current
model does not classify correctly — that is their purpose in the package:

- A high-pitched scream read as a siren (low-vote suppressed).
- A very distant mortar blast read as background.
- Heavy glass breakage read as an explosion (low-vote suppressed).
- A distant ambulance read as background.
- A wind gust that briefly **does** raise a scream alert — a documented false
  positive.

Do not retune the model or alert gates to make these pass during the
presentation; the project's acoustic documentation covers the limits and the
replacement path. Presenting them builds more credibility than hiding them.

## 8. Licenses and attribution

Each recording carries its own license (also in `manifest.tsv`):

- **CC0 1.0** (public domain): fsd-320167 joshs, fsd-238228 eaglechopper,
  fsd-367624 FxKid2, fsd-378257 13GPanska_Kubinova_Karolina, fsd-381853 sqeeeek.
- **CC BY 3.0** (attribution required): fsd-264918 AderuMoro, fsd-256296
  YleArkisto, fsd-249745 samvanzoest, fsd-426167 Nanashi, fsd-110612
  soundscalpel.com.

All ten files are from [Freesound](https://freesound.org) via the FSD50K
dataset (CC BY 4.0 compilation). The WAVs are byte-identical to the project's
audited held-out recordings (SHA-256 in `manifest.tsv` and
`datasets/manifest.json`), so redistribution is permitted and reproducible.
Recordings were intentionally not cropped, edited or relabeled.

## 9. Troubleshooting

| Problem | Fix |
|---|---|
| `A graphical desktop is required` | You are on a headless machine; use `--classify` (works headless) or run on a desktop. |
| No sound during `--demo` | Check OS output device/volume, or uncheck **Play audio** — classification still runs and saves. |
| Result differs from the manifest | Ensure JDK 21, the JAR built from `feat/classroom-demo`, and the file's SHA-256 matches `manifest.tsv`. Modified models/training are not comparable. |
| `--demo` complains about `--training` | Classroom Demo always uses its own bundled model; omit `--training`. |
| CSV not updating | The bottom of the Demo window shows the exact isolated CSV path; normal `data/events.csv` is only used by the regular app. |

## 10. Rebuilding this ZIP (maintainers)

From the repository root:

```sh
java scripts/PackageSoundset.java
```

This verifies every source WAV against the committed SHA-256 values, stages the
guide, checklist and manifest, and writes `OmniEar-Demo-Soundset.zip`. The five
`wav/` recordings are the same files Classroom Demo bundles; the five
`known-limitations/` recordings remain outside the app's five-button flow.
