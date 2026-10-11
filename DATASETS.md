# Real recordings and honest evaluation

## Continuous-detection extension (2026-10-11)

**316 licensed recordings: 217 training, 58 development, 41 new final-test.**
The original `held-out/` directory is retained for reproducibility but its 50
already-inspected files are now development data, alongside eight new background
files in `development/`. Only `final-test/` is the new evaluation set.

| Class | Training (groups) | Development | New final (groups) |
|---|---:|---:|---:|
| Distress scream | 40 (14) | 10 | 8 (7) |
| Explosion | 22 (3) | 10 | 2 (2) |
| Impact/crash | 40 (13) | 10 | 8 (8) |
| Traffic siren | 33 (21) | 10 | 3 (2) |
| Background | 82 (72) | 18 | 20 (20) |

All original assets are preserved. Added 42 real training hard negatives include
traffic/horns, rain/wind, thunder, door squeaks/closures, barking, speech/cheering,
knocks/hammering and fireworks. These are **non-target** sounds under this narrow
five-class definition, not proof that fireworks or thunder are harmless.
Four additional documented explosive recordings and one recorded NYC siren loop
remain with their original uploaders in training. Edited recordings are not
counted as independent sessions. Explosion training still has only three groups.

Final explosions are a railway controlled blast (Benboncan) and a documented
film-set pyrotechnic fireball (misosound); final sirens are two Dutch fire-truck
recordings (Sandermotions) and a search-and-rescue siren excerpt (daveincamas).
These very small supports do not establish broad class generalization.
All 316 clips are CC BY 3.0 (171) or CC0 (145); full provenance/hashes/credits are
in the manifest and attribution. No generated training sounds were added.

Selection was frozen using metadata, before final predictions. During the
metadata-only audit, candidate IDs 381951, 379015 and 380664 were replaced with
90790, 372919 and 43806 to conservatively avoid possible UPF/school-session
overlap with development. No scores informed these replacements. Acquisition
and checks use the existing verified-range/CRC/checksum workflow; same-uploader
and identified source-group separation covers all four directories.
The experiment contract is [evaluation/PROTOCOL.md](evaluation/PROTOCOL.md).

## Historical baseline subset (2026-10-10)

**220 real FSD50K recordings are included: 170 training, 50 held out.** They are
full-length source clips converted to mono 16 kHz PCM16 WAV, with no gain
normalization, event synthesis, cropping or training augmentation. Approximately
55 MiB total (40 MiB training); each file is under 1 MiB. Build/runtime is JDK-only.

| Class | Training clips | Training groups | Held-out clips | Held-out groups |
|---|---:|---:|---:|---:|
| Distress scream | 40 | 14 | 10 | 10 |
| Explosion | 18 | 3 | 10 | 4 |
| Impact/crash | 40 | 13 | 10 | 10 |
| Traffic siren | 32 | 21 | 10 | 10 |
| Background | 40 | 30 | 10 | 10 |

Counts are **recordings, not independent sessions**. There are 75 training groups
and 43 held-out groups across classes; some groups span multiple classes. No
uploader or identified source/session group appears in both splits.

### Provenance and permission

- Source: [FSD50K v1.0, DOI 10.5281/zenodo.4060432](https://zenodo.org/records/4060432),
  Eduardo Fonseca, Xavier Favory, Jordi Pons, Frederic Font and Xavier Serra.
  Cite: *FSD50K: an open dataset of human-labeled sound events*, IEEE/ACM TASLP
  30, 829–852 (2022).
- Collection/annotations: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
  retained in [datasets/LICENSE-DATASET](datasets/LICENSE-DATASET).
- Selected audio only: **128 CC BY 3.0; 92 CC0 1.0**. No NC, ND, Sampling+, or
  unknown-license audio is included. See [datasets/ATTRIBUTION.md](datasets/ATTRIBUTION.md)
  for every author/title/source/license. Format conversion is our only change.
- [datasets/manifest.json](datasets/manifest.json) records original labels,
  original descriptions, label rationale, original source ID, uploader, source
  split, project split/group, duration, URL, license, source SHA-256, converted
  WAV SHA-256 and decoded PCM SHA-256 for **every recording**.
- [datasets/selection.json](datasets/selection.json) is the frozen curated ID
  list. [datasets/groups.tsv](datasets/groups.tsv) is the Java development-check
  input. Raw downloads and larger archives stay in ignored `data/downloads/`.

### Semantic curation and exclusions

FSD50K human annotations were cross-checked with uploader descriptions. This is
metadata-based curation, **not an independent listening-panel validation**.
Broad parent labels alone were insufficient: `Explosion` includes gunshots,
fireworks, balloon pops and synthesized effects; `Screaming` includes cheering.

- **Scream:** recorded human fear/pain/panic or acted horror screams. Most are
  performances, not documented emergencies; screaming does not establish distress.
  Excluded crowd excitement, metal singing, monsters and ambiguous vocalizations.
- **Explosion:** documented physical detonations, grenades, military explosive
  impacts and one water-dynamite recording. Excluded gunfire, fireworks,
  balloon/bag pops, thunder, synthesized booms and ambiguous film/game composites.
- **Impact/crash:** actual glass/ceramic impacts and breakage. These are legitimate
  impacts, but **not a vehicle-crash corpus**; no collision-recognition claim.
- **Siren:** recorded road/emergency-vehicle sirens. Excluded building/car alarms,
  musical or synthesized sirens and ambiguous loops.
- **Background:** rain/wind/traffic, ordinary speech, laughter and handclaps;
  target-event labels excluded. Includes hard negatives rather than just silence.

The >=40 training target was met for three classes. The reviewed, clearly
documented redistributable selection yielded only **18 explosions and 32 sirens**.
We kept this deficit rather than counting crops, duplicates, synthetic effects
or vaguely similar sounds as genuine recordings. ESC-50/UrbanSound8K cannot fill
all five semantic classes; NIGENS has NC-ND restrictions. Alternative explosion
sources inspected included USC Red Library (film effects with uncertain physical
source for many clips) and BigSoundBank (agricultural bird-scarer blasts); neither
was silently substituted for genuine explosive-event coverage.

### Split and deduplication

Selection and source-group assignment preceded the final test evaluation.
Mostly FSD50K dev → training and eval → held-out, with seven dev explosions
from **Nanashi and YleArkisto reserved entirely for testing**. This is a custom
subset, not the official FSD50K benchmark. All qubodup recordings stay in training;
their referenced military videos and edited variants cannot cross the split.
Known same-source variants were omitted. The UPF scream workshop is grouped
across uploaders; possible workshop clip 220306 was excluded from the holdout
before evaluation. Held-out replacement 321230 is a different uploader/session.

`prepare_data.py --verify-only` checks frozen selection, WAV inventory, hashes,
decoded PCM uniqueness, uploader separation and source/session group separation.
The application additionally excludes byte-identical train/test overlap and
duplicate tests. Unknown cross-uploader copies/re-encodings cannot be ruled out
solely by weak metadata: this limitation is not claimed solved by a hash.

### Reproduce without manual downloading

A fresh clone already includes all required WAVs. To reacquire missing files:

```sh
# One-time preparation only: Python 3.10+ standard library and ffmpeg on PATH.
python scripts/prepare_data.py
python scripts/prepare_data.py --verify-only
java Build.java test
java -cp build/classes scripts/DevelopmentCheck.java
java -cp build/classes omniear.Main --evaluate datasets/held-out --training datasets/training
```

The script automatically downloads/checks the published metadata ZIP checksums,
reads the split-audio ZIP central directories using verified HTTP byte ranges,
fetches only selected members, validates ZIP CRC/size, converts with ffmpeg and
organizes the WAVs. It retries network failures and refuses a server that ignores
Range instead of downloading the entire ~25 GB collection. Existing modified
assets are rejected rather than overwritten. Full acquisition was executed here;
no Freesound account, API key or paid access was needed.

The final model uses training-only standardization and class-balanced inverse-
distance k=7 voting, selected with training-group-out validation before opening
the 50-file test results. No test-driven threshold changes were made. Full direct
and pipeline metrics are in [TEST_RESULTS.md](TEST_RESULTS.md) and
[evaluation/held-out.txt](evaluation/held-out.txt).

## Other public sources / manual expansion

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

The report includes direct and gated confusion matrices, per-class precision/recall,
false positives, per-file predictions, vote shares, distances and eligible/skipped
counts. It replays both stages in memory and does not persist evaluation events.
It also reports a clearly separate diagnostic with three seconds of digital
silence before each real clip; that artificial context is not live performance.
Copied/renamed training WAVs and duplicate
test files are excluded using SHA-256. This **cannot detect re-encoding or related
excerpts**, so the source-group split is still required. Do not tune on the final
held-out set. These are results for your chosen subset, not published benchmark
results or evidence of emergency-response reliability.

## Historical pre-training real-file check

Downloaded and processed [ESC-50 `1-31482-A-42.wav`](https://raw.githubusercontent.com/karolpiczak/ESC-50/master/audio/1-31482-A-42.wav),
a `siren` recording derived from **dobroide's `20070224.siren.flac`**,
[Freesound 31482](https://freesound.org/people/dobroide/sounds/31482/) (original
credit CC BY; ESC-50 collection CC BY-NC 3.0).
The unmodified file remained in `/tmp/opencode/`, outside this repository.
Java decoded/resampled it to 80,000 samples (5 s), RMS 0.239526, and extracted
the 60-feature vector successfully. Gated analysis completed with no RMS event.
This initial check verified only WAV/DSP plumbing. The new model uses the full
FSD50K source 31482 in training, not the earlier ESC-50 excerpt, and no related
source is in the held-out set.
