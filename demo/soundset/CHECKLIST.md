# Soundset verification checklist

Print or copy this list. Each line has an exact expected value; compare with
what the app actually shows. Anything unexpected: check JDK 21, use the JAR
built from `feat/classroom-demo`, and compare SHA-256 with `manifest.tsv`.

## A. Extract and identify

- [ ] `OmniEar-Demo-Soundset.zip` extracts cleanly on this machine.
- [ ] `manifest.tsv` opens and lists 10 files with licenses and SHA-256.
- [ ] `wav/` has exactly 5 files; `known-limitations/` has exactly 5 files.

## B. Classroom Demo predictions (java -jar OmniEar.jar --demo)

- [ ] Window reaches **Ready · choose recording 1 to begin**.
- [ ] Button 1 plays fsd-264918 and shows **Distress scream / P0 / Critical / ALERT**.
- [ ] Button 2 plays fsd-256296 and shows **Explosion / P0 / Critical / ALERT**.
- [ ] Button 3 plays fsd-320167 and shows **Impact / crash / P1 / High / ALERT**.
- [ ] Button 4 plays fsd-238228 and shows **Traffic siren / P4 / Monitoring / ALERT**.
- [ ] Button 5 plays fsd-110612 and shows **Background noise · No threat alert
      · BACKGROUND_SUPPRESSED**.
- [ ] Repeat one button: exactly one new event appears, no duplicate or
      overlapping run.

## C. Alerting and suppression

- [ ] All four threat recordings produce an ALERT row with the priority above.
- [ ] The background recording produces **no threat alert** (row still listed).
- [ ] Event history shows the same categories/priorities as the result panel.
- [ ] Vote share is shown as agreement (≤ 1.000), never called a probability.

## D. CSV persistence

- [ ] The bottom label shows an isolated CSV path
      (`.../classroom-demo/session-.../events-....csv`).
- [ ] Open that file: it has a header plus one row per classification just made.
- [ ] Click **Reset Demo**: history and waveform clear, the CSV path changes.
- [ ] The previous CSV file still exists unchanged on disk.
- [ ] Close the app and relaunch: a new empty session starts (previous results
      remain on disk under their earlier session folder).

## E. Manual GUI classification (WAV Testing tab)

- [ ] **Classify WAV…** on `wav/3-glass-break-impact__fsd-320167.wav` shows
      **Impact / crash**, matching `manifest.tsv`.
- [ ] **Classify WAV…** on `wav/5-background-rain__fsd-110612.wav` shows
      **Background noise / BACKGROUND_SUPPRESSED**.
- [ ] The new event appears in the Alert History tab and in `data/events.csv`.

## F. Known limitations (expected mismatches — do not "fix")

- [ ] `challenge-scream-boy__fsd-249745.wav` → predicted **siren**, suppressed.
- [ ] `challenge-explosion-distance__fsd-426167.wav` → predicted **background**,
      suppressed.
- [ ] `challenge-heavy-glass__fsd-367624.wav` → predicted **explosion**,
      suppressed.
- [ ] `challenge-siren-ambulance__fsd-378257.wav` → predicted **background**,
      suppressed.
- [ ] `challenge-wind__fsd-381853.wav` → predicted **scream with P0 ALERT**
      (documented false positive).

## G. Optional: exact command-line comparison

- [ ] `java -jar OmniEar.jar --classify wav/1-scream__fsd-264918.wav --data /tmp/omniear-check`
      ends with `scream_distress · P0 · votes 0.716 · distance 0.981 · ALERT`.
- [ ] The values above match the vote/distance columns in `manifest.tsv`
      (3-decimal display).
