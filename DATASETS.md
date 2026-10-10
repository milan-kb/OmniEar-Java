# Real recordings and honest evaluation

The local `data/` directory was inspected: it contained event metadata, **no
training WAVs**. No five-class acoustic model or accuracy result is bundled.
The sources below were checked online during this revision. Download audio
separately; the application itself works offline and never downloads recordings.

## Verified public sources

| Source / download | Useful labels | License / preparation |
|---|---|---|
| [FSD50K, Fonseca et al.](https://zenodo.org/records/4060432) — audio archives, ground truth, metadata and documentation on the record (24.7 GB total) | `Screaming`, `Explosion`, `Shatter`, `Siren`; selected `Rain`, `Wind`, `Speech`, `Engine` for background | Dataset curation CC BY 4.0; **each recording has its own license**, including CC0, CC BY, CC BY-NC and CC Sampling+. Keep `dev_clips_info_FSD50K.json` / `eval_clips_info_FSD50K.json` attribution records. Audio is PCM16, 44.1 kHz mono, 0.3–30 s and loads directly. |
| [ESC-50, K. J. Piczak](https://github.com/karolpiczak/ESC-50) — [ZIP (~600 MB)](https://github.com/karolpiczak/ESC-50/archive/master.zip) | `siren`, `glass_breaking`; selected rain/wind/ordinary household sounds for background | Dataset CC BY-NC 3.0; ESC-10 subset CC BY 3.0. Retain the repository LICENSE with clip-level credits. PCM WAV, 44.1 kHz mono, 5 s. **Does not cover all five OmniEar classes.** |
| [NIGENS, Trowitzsch et al.](https://zenodo.org/records/2535878) — [ZIP (2.2 GB)](https://zenodo.org/records/2535878/files/NIGENS.zip?download=1) | Female/male scream, crash; selected non-threat classes | CC BY-NC-ND 4.0. Respect attribution, noncommercial and no-derivatives conditions; do not redistribute edited excerpts as a new dataset. Mostly 32-bit, 44.1 kHz WAV. Some files exceed OmniEar's 120 s limit; choose shorter eligible originals. Its `general` class includes guns and other foreground events: do not label it wholesale as background. |

FSD50K's downloaded `vocabulary.csv` was checked for the labels above. `Crash_cymbal`
is a musical instrument, **not** a collision. Scream labels do not prove distress;
inspect the sound and record the mapping limitations. Review multi-label files and
exclude ambiguous clips containing competing target events. Fireworks, gunshots,
alarms and baby crying are not automatic substitutes for the requested labels.

## Prepare training and unseen tests

1. Download the selected source's audio, labels, attribution and split metadata.
   Use a small curated subset rather than loading the entire collection (maximum
   5,000 usable training WAVs). Preserve author, URL, source ID, license, original
   label and chosen OmniEar label in your own manifest.
2. Split **by original recording/source/uploader before importing**. For FSD50K,
   use its development train/validation separation for development and its
   uploader-disjoint evaluation set for final testing. For ESC-50, keep folds
   1–4 for development and fold 5 for held-out testing; never scatter excerpts
   of a source across splits. For own recordings, hold out recording sessions.
3. Create two separate, non-nested roots with the same five subdirectories:

   ```text
   data/training/                 data/held-out/
     scream_distress/               scream_distress/
     explosion/                     explosion/
     impact_crash/                  impact_crash/
     siren_traffic/                 siren_traffic/
     background/                    background/
   ```

4. In **Training**, choose a label and import only training recordings, or select
   the prepared training root. One usable WAV per class unlocks the model;
   collect multiple independent examples per class for a meaningful experiment.
   Read the import log and readiness counts. Duplicate byte-identical WAVs count
   once; conflicting labels prevent model loading until corrected.
5. Supported PCM formats are decoded, downmixed and resampled in Java to
   16 kHz mono **in memory**, without changing the original or imported WAV.
   No per-file gain normalization is applied. Compressed WAV/MP3/OGG/FLAC need
   separately obtained PCM WAV versions; the application does not claim to
   decode these formats. Short clips are padded to the 2.5-second feature window.
6. Use **WAV Testing → Evaluate held-out folder**, or:

   ```sh
   java Build.java build
   java -cp build/classes omniear.Main --evaluate data/held-out --training data/training
   ```

The report includes per-file predicted labels, vote shares, feature distances,
confusion matrix, missing test classes, evaluated/skipped counts and an observed
accuracy only when eligible test files exist. Evaluation bypasses Stage 1 and
does not generate alerts or CSV events. Copied/renamed training WAVs and duplicate
test files are excluded using SHA-256. This **cannot detect re-encoding or related
excerpts**, so the source-group split is still required. Do not tune on the final
held-out set. These are results for your chosen subset, not published benchmark
results or evidence of emergency-response reliability.

## Real-file check performed

Downloaded and processed [ESC-50 `1-31482-A-42.wav`](https://raw.githubusercontent.com/karolpiczak/ESC-50/master/audio/1-31482-A-42.wav),
a `siren` recording derived from **dobroide's `20070224.siren.flac`**,
[Freesound 31482](https://freesound.org/people/dobroide/sounds/31482/) (original
credit CC BY; ESC-50 collection CC BY-NC 3.0).
The unmodified file remained in `/tmp/opencode/`, outside this repository.
Java decoded/resampled it to 80,000 samples (5 s), RMS 0.239526, and extracted
the 60-feature vector successfully. Gated analysis completed with no RMS event.
It was never used for training. This verifies a real WAV/DSP path, **not semantic
classification accuracy**; a complete curated five-class training/holdout set
is still needed for that evaluation.
