# Replacement path if reliable recognition is required

The retained JDK-only repair is **not a reliable recognizer**. On the fresh 42
files it improves raw startup routing and background specificity, but five of
eight direct screams still become sirens; conservative gates reject most true
screams/explosions. Continuous target recall falls 9/18 → 2/18 while background
alerts fall 11 → 2. Those few minutes cannot establish an acceptable deployed
false-alarm rate. This is evidence against this feature/model/data combination,
not a proof that every possible JDK-only classifier is inadequate.

## Concrete Java-compatible option — not installed in this branch

Use Google's pretrained **YAMNet v1** as a PCM/embedding model, with a small
source-independent five-class head and separately calibrated rejection policy.
Official source/model documentation:

- <https://www.tensorflow.org/hub/tutorials/yamnet>
- <https://tfhub.dev/google/yamnet/1>
- <https://github.com/tensorflow/models/tree/master/research/audioset/yamnet>
- Java SavedModel loading: <https://www.tensorflow.org/jvm>
- Optional ONNX Java backend: <https://onnxruntime.ai/docs/get-started/with-java.html>

YAMNet supplies 521 AudioSet scores and 1024-dimensional embeddings. It consumes
16 kHz mono PCM in [-1,1], not this application's 60 pooled MFCC values. Its
frontend uses magnitude STFT, periodic Hann, 64 mel bands (125–7500 Hz),
`log(mel + .001)`, 0.96 s patches at 0.48 s hop; the first unpadded patch needs
0.975 s of waveform. **Do not reuse our power-MFCC or RMS-.1 transform for it.**

1. Start a separate opt-in backend branch. Keep Java 21, Swing, AudioSource,
   MonitorService and the metadata/persistence path. Add an explicit raw-PCM
   inference boundary to DetectionPipeline; don't squeeze embeddings into the
   existing FeatureVector contract or label CNN scores as k-NN vote shares.
2. Prefer loading the official SavedModel using TensorFlow Java's
   `org.tensorflow:tensorflow-core-platform` (native CPU runtime). Pin the exact
   runtime/model versions and SHA-256 after a Java 21/platform compatibility
   spike; inspect the actual SavedModel signature and input/output names.
   This is a requested **future exception to JDK-only**, not an added dependency.
3. If using ONNX instead, the concrete Java artifact is
   `com.microsoft.onnxruntime:onnxruntime`; use a pinned, audited offline export
   of the official weights. Inspect `OrtSession.getInputInfo()` and use
   `OnnxTensor`/`OrtSession.run()` with try-with-resources. Conversion is **not
   verified here**: require Python-reference/Java output parity on identical PCM
   (including startup, short padding, silence and clipping) before accepting it.
   Never substitute an unverified third-party model file silently.
4. Preserve model, code and AudioSet-map licenses/attribution and document any
   FSD50K/AudioSet source overlap. External pretraining may overlap public test
   audio; obtain new session-disjoint local recordings for final validation.
5. Train a regularized head on embeddings with genuine independent recordings,
   especially new physical-explosion and traffic-siren sources and diverse
   hard negatives. Don't identify distress, vehicle crashes or harmlessness from
   these acoustic labels alone. The model's class scores are not emergency risk.
6. Fit per-class score calibration (e.g. held-source logistic/Platt calibration)
   and choose precision/recall/rejection points **only on development sessions**.
   Freeze them before a new blind final. Use separately annotated onsets/offsets
   for latency/instance recall and many independent hours of negative audio,
   not repeated loops, for false alerts/hour. Agree on the acceptable risk first.
7. Keep the bounded worker and episode suppression, but validate its cadence and
   class-specific episode offsets with the new frontend. Check native CPU stalls
   against the microphone's buffer, long-duration memory/resource closure,
   real microphone gain/device identity and each supported OS.
8. Version score metadata explicitly if extending CSV; continue reading old CSV
   columns/statuses without rewriting history. Do not write neural confidence
   into `vote_share` or pretend `nearest_distance=0` means a CNN exact match.

No pretrained model, conversion, dependency or performance improvement is
claimed tested or introduced by this document. It is the concrete next route
for the recognition failures that this repair did not solve.
