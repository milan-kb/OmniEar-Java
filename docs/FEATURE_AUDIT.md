# Submission feature and claim audit

Scope: academic submission behavior after `fb5246f`, retained by the integrated
cleanup `87ff0fb9`; no classifier/feature/detector tuning.
Paths below are relative to `src/omniear/`. Historical evaluation reports are
preserved as dated evidence, not promises about the live demonstration.

| Feature / control | Implementation | Actual behavior / qualification |
|---|---|---|
| Launch, CLI flags, help | `Main`, `Build.java` | GUI, train, classify, analyze, evaluate, mic-test; full JDK 21; no runtime downloads. |
| Automatic training | `model/TrainingRepository.installBundled` | Only installs into an absent default directory; preserves existing/custom work; 170 accepted WAVs. |
| Loading/ready/untrained header | `ui/Dashboard`, `ui/TrainingPanel` | Shows loading and sample/class coverage; unavailable model means anomaly-only mode. Readiness is not accuracy. |
| Start monitoring / Stop | `Dashboard.startSource`, `MonitorService` | Uses OS default input; first 20 blocks calibrate. Stop closes/unblocks input and discards unfinished capture. |
| Five-second microphone test | `MonitorService.testMicrophone`, `audio/InputDiagnostics` | No classifier/event persistence; sample-weighted RMS, dBFS, peak, ≥0.99 absolute sample fraction and OS gain guidance. |
| Waveform / meter | `ui/WaveformPanel`, `Dashboard.refreshSignal` | Actual PCM, fixed full-scale amplitude; latest block coalesced on EDT. No simulated animation or acoustic SPL measurement. |
| Threshold / floor / cooldown spinners | `Dashboard.config`, `AdaptiveDetector.Config` | Enabled while idle; affect the next monitoring/replay session only. They do not change direct classification or training. |
| Classify WAV | `ui/WavTestingPanel`, `DetectionPipeline.classifyClip` | Stage 1 bypass; 2.5 s feature window, real label/vote/distance/status; CSV source includes Stage 1 bypass. No playback. |
| Guided sample selector / Classify example | `model/DemoSample`, `WavTestingPanel`, `demo/samples.tsv` | Ten separately packaged, licensed, inspected recordings; fresh inference with active model; visible label comparison and attribution. No cached/faked predictions. |
| Analyze WAV | `Dashboard.startWav`, `MonitorService`, `audio/WavSource` | Faster-than-real-time PCM replay through the microphone detector; sample-clock timing, not audible playback. No-event completion is possible. |
| Evaluate held-out folder | `model/HeldOutEvaluation` | Separate/non-nested roots, byte-overlap exclusion, raw and digital-quiet diagnostics; missing classes shown. No training or persistent alerts. Existing test sets are inspected. |
| Choose category / Add labeled WAVs | `TrainingPanel`, `TrainingRepository.importWav` | Explicit labels; validated staged copies, SHA-256 names, duplicate/conflicting-label checks. Writes active training root only. |
| Use training folder / Reload | `TrainingPanel` | Loads actual files in background; failed reload invalidates the active classifier instead of continuing with a stale one. |
| Five coverage rows / progress / log | `TrainingPanel` | Counts of usable files, not model quality. Bad files are skipped with diagnostics; missing classes disable predictions. |
| Dashboard event table / counters | `ui/EventTableModel` | Last ≤1,000 events, including suppressed/unclassified/direct tests; sorting, horizontal scroll; no fleet/map or synthetic statistics. |
| Alert History / latest alert | `Dashboard`, `alerts/AlertPolicy` | Filters actual ALERT events; P0/P1/P4 local labels only, no siren playback, notifications, SMS or remote dispatch. |
| Diagnostics / CSV banner | `Dashboard`, `alerts/EventStore` | Explicit disabled-persistence state or write error; malformed CSV not discarded; metadata-only storage. |
| Alt+1…4 / button mnemonics / dividers | Swing actions in UI classes | Keyboard mnemonics and labeled controls; draggable/scrollable dashboard and WAV results. OS focus required; no claim of assistive-technology certification. |
| Close window | `Dashboard.closeApplication` | Cancels worker panels, stops monitoring, disposes GUI, asynchronously awaits audio and closes CSV. |

## Claim corrections made for submission

- “Normalized WAV” in clip output could imply gain normalization. It now says
  sample-rate/channel conversion **without** gain normalization.
- Default input is described as the **OS default recording input**, not proof
  of a particular physical laptop microphone (it might be a loopback device).
- Microphone diagnostics no longer print a misleading “untrained” model warning;
  they state that the classifier is not used.
- Ready/coverage indicators explicitly separate usable class coverage from
  measured reliability. Training reload shows a paused/loading state.
- README's blanket clipping statement was stale; individual diagnostic records
  are dated evidence. Readiness ≠ accuracy, votes ≠ probabilities, direct WAV
  classification ≠ continuous detection, and dBFS ≠ acoustic dB SPL.
- Training failure actually clears the classifier; the old README phrase
  “replaced only after successful reload” was incomplete and has been corrected.

## Source boundaries checked

The FFT/MFCC extractor, k-NN arithmetic, `AdaptiveDetector`, `DetectionPipeline`,
`AlertPolicy`, PCM decoder/resampler and the 170 training WAVs are unchanged from
`fb5246f`. Cleanup only localizes constructor-only k-NN state and shares test
search/polling mechanics. Presentation, read-only input diagnostics, demo resource packaging and
tests/documentation are the submission changes. The demo set is not added to the
training ZIP or model. Provenance remains in the original manifests.

## Verification boundaries

The regression suite covers underlying control actions and persistence; Swing
tests cover displayed tabs, actual inference, error recovery, keyboard navigation,
readiness, demo button, history and cleanup. Not every native file chooser or
every OS permission configuration is automated. Component renders verify the
application window without capturing other desktop content. Screen-reader use,
other operating systems, long-duration microphone stability and real acoustic
event detection remain unverified. See `../TEST_RESULTS.md` for the exact final
run evidence, including any environment-specific limitations. New teammates can
start with [Setup](GETTING_STARTED.md), [Architecture](ARCHITECTURE.md) and
[Testing](TESTING.md); this audit is a control-to-code reference, not a setup guide.
