# Continuous detection experiment contract

Baseline: 5a5b764. Runtime remains Java 21 FFT/MFCC + balanced k-NN.
Assumptions: FSD50K labels and descriptions are weak recording-level labels;
replay insertion time is NOT an independently annotated acoustic onset. Glass
breakage and acted screams retain the existing semantic limitations.

The previously inspected 50-file `held-out` set is now **development only**.
Eight new independent background recordings extend development. Frozen new
`final-test` IDs are selected from metadata before any model evaluation. No
final-test audio, predictions or scores may inform feature/window/threshold
selection. Same uploader and known workshop/source groups never cross splits.
Related edits stay in their original split. Final sources are not trained.
Before final inference, the metadata audit replaced possible school/workshop
candidates 381951/379015/380664 with 90790/372919/43806. No acoustic scores were
consulted. Final support is 8 screams, 2 explosions, 8 breakages, 3 sirens and
20 backgrounds; small explosion/siren supports are explicitly a limitation.

Compare baseline gating, periodic windows and hybrid onset + periodic windows.
Keep computation below one classification per 0.5 s (never every audio frame).
Use the identical feature/window transform in training and streaming inference.
Select on development correctly routed recording recall, subject to no increase
in background false alerts over the paired baseline for each replay scenario.
If that constraint fails, reject the candidate; final evaluation is report-only.

Report direct confusion/precision/recall separately from replay: raw recordings,
3 s digital quiet prelude, gain changes, real background beds and continuous
multi-recording streams. Count any alert on background as false; report actual
background exposure seconds and alerts/hour, not extrapolated deployment claims.
Deduplicate event alerts explicitly. Count missed target recordings, first-label
confusion including abstentions, correctly routed alerts and latency from file
insertion to emission. Latency is a replay proxy (weak labels have no onset).
Report all scenarios even if worse. Repeated/gain/mixed replay is not extra data.

Final checks: data integrity and source separation, mechanics/regression tests,
`java Build.java test`, `ui-test`, `jar`, fresh trained startup and physical
microphone diagnostic. Physical diagnostic only establishes input operation.

Outcome: the final quality gate failed; the periodic candidate was reverted.
The data-only expansion also failed the development false-alert constraint.
See `DECISION.md`; current runtime retains the original 170-recording model.
