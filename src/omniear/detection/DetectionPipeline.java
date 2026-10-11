package omniear.detection;

import java.time.Instant;
import java.util.*;
import omniear.audio.AudioSamples;
import omniear.alerts.AlertPolicy;
import omniear.model.*;

/** Sample-clock capture: bounded 3.5 s ring, 1.25 s post-trigger wait, 2.5 s loudest window. */
public final class DetectionPipeline {
    private static final int POST_SAMPLES = 20000;
    private final float[] ring = new float[56000];
    private final AdaptiveDetector detector;
    private final SoundClassifier classifier;
    private final FeatureExtractor extractor = new FeatureExtractor();
    private final AlertPolicy alerts = new AlertPolicy();
    private final Instant started;
    private final String source;
    private int ringSize, ringPosition;
    private long totalSamples;
    private long triggerEnd = -1;
    private double triggerSeconds;
    private AdaptiveDetector.Reading triggerReading;
    private AdaptiveDetector.Reading lastReading;

    public DetectionPipeline(AdaptiveDetector.Config config, SoundClassifier classifier, Instant started, String source) {
        detector = new AdaptiveDetector(config);
        this.classifier = classifier;
        this.started = Objects.requireNonNull(started);
        this.source = Objects.requireNonNull(source);
    }
    public AdaptiveDetector.Reading lastReading() { return lastReading; }
    public double audioSeconds() { return totalSamples / (double) AudioSamples.SAMPLE_RATE; }

    /** Explicit clip inspection route for short WAVs that have no calibration prelude. */
    public static SoundEvent classifyClip(float[] audio, SoundClassifier classifier, Instant timestamp, String source) {
        var reading = new AdaptiveDetector.Reading(AudioSamples.rms(audio), 0, 0, false, false);
        var prediction = classifier == null ? null : classifier.classify(new FeatureExtractor().extract(audio));
        return new AlertPolicy().event(timestamp, source + " [clip; Stage 1 bypass]", 0, reading, prediction);
    }

    public Optional<SoundEvent> accept(float[] block) {
        if (block.length == 0 || block.length > AudioSamples.BLOCK_SIZE) throw new IllegalArgumentException("Expected up to 100 ms of audio");
        double blockTime = audioSeconds();
        lastReading = detector.accept(block, blockTime);
        for (float value : block) {
            ring[ringPosition] = value;
            ringPosition = (ringPosition + 1) % ring.length;
            ringSize = Math.min(ring.length, ringSize + 1);
        }
        totalSamples += block.length;
        Optional<SoundEvent> completed = triggerEnd >= 0 && totalSamples - triggerEnd >= POST_SAMPLES ? finishCapture() : Optional.empty();
        if (lastReading.triggered() && triggerEnd < 0) {
            triggerEnd = totalSamples;
            triggerSeconds = blockTime;
            triggerReading = lastReading;
        }
        return completed;
    }

    /** At WAV EOF emit an outstanding event; Stop deliberately discards unfinished capture. */
    public Optional<SoundEvent> finish() { return triggerEnd >= 0 ? finishCapture() : Optional.empty(); }

    private Optional<SoundEvent> finishCapture() {
        triggerEnd = -1;
        if (ringSize < AudioSamples.SAMPLE_RATE / 2) return Optional.empty();
        float[] captured = new float[ringSize];
        int first = (ringPosition - ringSize + ring.length) % ring.length;
        for (int i = 0; i < ringSize; i++) captured[i] = ring[(first + i) % ring.length];
        SoundClassifier.Prediction prediction = classifier == null ? null : classifier.classify(extractor.extract(captured));
        return Optional.of(alerts.event(started.plusNanos(Math.round(triggerSeconds * 1_000_000_000)), source, triggerSeconds, triggerReading, prediction));
    }
}
