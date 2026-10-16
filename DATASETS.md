# Real recordings and honest evaluation

## Acoustic repair extension (2026-10-16) — current

**406 unique licensed recordings**: original 364 preserved plus 42 source/uploader-
disjoint `acoustic-final` files (8 screams, 10 glass/ceramic impacts, 24 backgrounds).
The metadata-only selection froze at `102e86f`; runtime/model/gates froze at
`c1f0200` before final inference. All sets are now inspected. New final has no
explosion or siren positives; their independent recall remains unmeasured.
New files: 27 CC0 and 15 CC BY 3.0; totals: 198 CC0 and 208 CC BY 3.0.

The current bundle uses **217 training recordings**, the original 170 plus all
47 existing `additional-training` files: 40/22/40/33/82 by category. No test audio
enters training; all original WAVs and manifest entries are unchanged. The
normalization is in shared feature extraction, not a rewrite of stored audio.
Existing user training roots are not overwritten or silently expanded. Use a
fresh `--data` root for the evaluated bundle. See the
[protocol](evaluation/ACOUSTIC_PROTOCOL.md), [results/decision](evaluation/ACOUSTIC_DECISION.md)
and [replacement path](evaluation/ACOUSTIC_REPLACEMENT.md).

Sections below preserve the earlier studies' data boundaries and observations;
their references to 170-file packaging and research-only additions are historical.

## Submission demonstration resources

`demo/samples.tsv` selects ten **already-inspected** recordings from the existing
holdout/previous-final directories, two per category: one observed direct-label
match and one mismatch. They are not new evaluation data or added training.
`Build.java` copies them into separate `omniear/demo/` resources for portable GUI
demonstrations. The training ZIP still contains exactly 170 WAVs. Five selected
clips are CC BY 3.0 and five CC0; the GUI/catalog show authors, source links and
licenses. Full original descriptions and hashes remain in the manifest below.
The collection still contains 364 unique recordings; packaging duplicates does
not increase sample counts. This deliberately selected demo cannot estimate accuracy.

## False-alert extension (2026-10-12)

**364 licensed recordings:** 170 accepted training, 47 research-only training,
99 development (50 original holdout + eight backgrounds + 41 previously inspected
final recordings), and 48 newly acquired final recordings in `rejection-final/`.
The new final is now inspected too; it must not be reused as an untouched test.

Selection froze at `9ac2ab0`, before inference and before the rejection candidate
froze at `4e4e17f`. The 48 files have 48 distinct uploaders, disjoint from all
previous splits: eight human screams, eight glass/bottle/light-bulb breakages,
two recorded traffic sirens and 30 backgrounds (dogs, thunder, spoken/cheerful
voices, doors, traffic, hammering, applause and rain). Mostly acted screams do
not establish distress; glass breakage does not establish vehicle collisions.
No confidently documented unused explosion source was found in the reviewed
permissible FSD50K metadata. Explosion final recall is **unmeasured**. Ambiguous,
synthetic/composite and firework/gunshot candidates were not relabeled to fill it.

New audio licenses: 22 CC BY 3.0 and 26 CC0; total: 193 CC BY 3.0 and 171 CC0.
Full recordings, verified archive acquisition and mono 16 kHz PCM16 conversion
follow the workflow below. All 316 previous manifest entries and WAV hashes are
unchanged. Metadata-based source/session review and decoded uniqueness audits
passed; unknown cross-uploader copies cannot be ruled out. Curation is not an
independent listening-panel validation. The new files are excluded from runtime
training and JAR audio packaging. See [the rejection study](evaluation/REJECTION_DECISION.md).

## Continuous-detection extension (2026-10-11)

**316 licensed recordings: 170 accepted training, 47 research-only additional
training, 58 development, 41 final-test.** The expanded-model/periodic candidate
failed the false-alert acceptance bar and was reverted. New training assets live
in `datasets/additional-training/` and are **not bundled or automatically loaded**.
The original `held-out/` directory is retained for reproducibility but its 50
already-inspected files are now development data, alongside eight new background
files in `development/`. Only `final-test/` is the new evaluation set.

| Class | Experimental combined training (groups) | Development | New final (groups) |
|---|---:|---:|---:|
| Distress scream | 40 (14) | 10 | 8 (7) |
| Explosion | 22 (3) | 10 | 2 (2) |
| Impact/crash | 40 (13) | 10 | 8 (8) |
| Traffic siren | 33 (21) | 10 | 3 (2) |
| Background | 82 (72) | 18 | 20 (20) |

The table describes the evaluated 217-file candidate. The shipped model remains
the original 170 recordings (40/18/40/32/40). All original assets are preserved.
Added 42 real research hard negatives include
traffic/horns, rain/wind, thunder, door squeaks/closures, barking, speech/cheering,
knocks/hammering and fireworks. These are **non-target** sounds under this narrow
five-class definition, not proof that fireworks or thunder are harmless.
Four additional documented explosive recordings and one recorded NYC siren loop
remain in the same logical training partition as their original uploaders.
The audit treats `additional-training` as training for source separation, while
inventory checks and runtime packaging distinguish the directories. Edited recordings are not
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
and identified source-group separation covers all curated directories.
The experiment contract is [evaluation/PROTOCOL.md](evaluation/PROTOCOL.md);
the rejection decision and full results are in [TEST_RESULTS.md](TEST_RESULTS.md).
The final set has now been inspected and must not be used to tune future models.

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
