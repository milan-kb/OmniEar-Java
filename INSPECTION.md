# Upstream inspection and implementation contract

Inspected https://github.com/milan-kb/OmniEar at commit
`c61171270377ab1f2c39b495e64fef9aa53a90d5` before implementation. The read-only
checkout is outside this project. No upstream files are modified or copied here.

## Complete repository inventory reviewed

- Root README, ignores, dependency lists and startup script.
- Runtime: `stage1_trigger.py`, `omniear_pipeline.py`, `audio_utils.py`,
  `threat_fusion.py`, `relay_server.py`.
- Dataset preparation: `download_datasets.py`, `fix_sesa.py`,
  `organize_screams.py`, `augment_data.py`, `extract_embeddings.py`.
- Training: `train_classifier.py`; checks: `test_stage1_guided.py`, both
  `tests/` files and `notebooks/test_yamnet.py`.
- Model assets: all five labels in `models/classes.json`; inspected the Keras
  archive manifest and layer configuration. Its 3,072-input dense head needs
  YAMNet mean/max/std embeddings; HDF5 weights alone cannot operate on MFCCs.
- Entire `acoustic-insight-sentinel/` source, components, routes, stores, styles,
  configuration, dependency/lockfile metadata and public assets. Operations has
  real WebSocket alerts, but waveform, fleet, maps and district analytics include
  simulated data. These simulations and test-alert fabrication are not ported.
- Research directory: paper inventory, redistribution notices and all seven PDF
  abstracts reviewed. Research is context, not executable detection logic or a
  training dataset. PDFs and model binaries are not redistributed.
- No WAV dataset, reusable Java implementation or upstream AGENTS.md is present.

## Preserved vs replaced

| Upstream behavior | Java implementation |
|---|---|
| 16 kHz mono, 100 ms blocks | Java Sound PCM input, normalized samples |
| 50 RMS blocks, first 20 calibration blocks | Same rolling baseline and warmup |
| threshold max(6 × baseline, 0.02), strict `>` | Same defaults, GUI calibration knobs |
| strict cooldown > 1.5 seconds; include spikes in baseline | Same, using audio sample time for reproducible WAV analysis |
| 3.5 s ring; wait 1.25 s after trigger | Same bounded capture; flush partial capture at EOF |
| Loudest 2.5 s based on rolling 100 ms RMS sums | Same selection and centered zero padding |
| YAMNet + neural head + semantic fusion | Explicit replacement: Java FFT/MFCC features + standardized k-NN |
| Mean/max/std temporal feature pooling | Preserved for handcrafted frame features |
| P0 scream/explosion, P1 crash, P4 siren | Preserved; background never alerts |
| Per-class confidence gates .52/.65/.69/.80 | Preserved as initial vote-share gates, NOT calibrated probabilities |
| Browser/WebSocket/Pi notifications | Local Swing dashboard + CSV only; no network/hardware |

## Acceptance bar / scope

Java 21 standard JDK only; no runtime/build dependencies, generated training
examples, unsupported accuracy claims or fake labels. Implement `audio`,
`detection`, `model`, `alerts`, `ui` packages. Compile with all javac warnings
treated as errors. Executable checks must cover detector boundaries, finite
features, spectral behavior, real k-NN mechanics, PCM decoding/resampling,
CSV round trips, missing/invalid training data, resource cleanup and integrated
WAV processing. Verify Swing on the EDT and on a real display when available.
No commits/pushes are requested; this empty workspace is not a Git repository.

Implementation slices: detector/windowing → audio decoding/capture → features
and training → event pipeline/persistence → Swing dashboard → integration and
runtime checks. No background predictions are substituted when training is absent.
