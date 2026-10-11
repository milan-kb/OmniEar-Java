package omniear.detection;

import java.time.Instant;
import java.util.*;
import omniear.audio.AudioSamples;
import omniear.alerts.AlertPolicy;
import omniear.model.*;

/** Trained: one-second trailing windows at 2 Hz. Untrained: original RMS anomaly capture. */
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
    private long nextClassification = AudioSamples.SAMPLE_RATE;
    private long lastClassification;
    private final long[] lastSupported = new long[Category.values().length];
    private Category previousCategory;
    private int supportedStreak;

    public DetectionPipeline(AdaptiveDetector.Config config, SoundClassifier classifier, Instant started, String source) {
        detector = new AdaptiveDetector(config);
        this.classifier = classifier;
        this.started = Objects.requireNonNull(started);
        this.source = Objects.requireNonNull(source);
        Arrays.fill(lastSupported, -160000);
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
        if (classifier != null) {
            if (totalSamples < nextClassification) return Optional.empty();
            nextClassification = totalSamples + AudioSamples.SAMPLE_RATE / 2;
            return classifyWindow();
        }
        Optional<SoundEvent> completed = triggerEnd >= 0 && totalSamples - triggerEnd >= POST_SAMPLES ? finishCapture() : Optional.empty();
        if (lastReading.triggered() && triggerEnd < 0) {
            triggerEnd = totalSamples;
            triggerSeconds = blockTime;
            triggerReading = lastReading;
        }
        return completed;
    }

    /** At WAV EOF emit an outstanding event; Stop deliberately discards unfinished capture. */
    public Optional<SoundEvent> finish() {
        if (classifier != null) return totalSamples > lastClassification ? classifyWindow() : Optional.empty();
        return triggerEnd >= 0 ? finishCapture() : Optional.empty();
    }

    private Optional<SoundEvent> classifyWindow() {
        lastClassification = totalSamples;
        int size = Math.min(ringSize, AudioSamples.SAMPLE_RATE);
        if (size == 0) return Optional.empty();
        float[] captured = new float[size];
        int first = (ringPosition - size + ring.length) % ring.length;
        for (int i = 0; i < size; i++) captured[i] = ring[(first + i) % ring.length];
        double rms = AudioSamples.rms(captured);
        if (rms < .0001) { previousCategory = null; supportedStreak = 0; return Optional.empty(); }
        var prediction = classifier.classify(extractor.extract(captured));
        Category category = prediction.category();
        boolean supported = AlertPolicy.supported(prediction);
        supportedStreak = supported ? (previousCategory == category ? supportedStreak + 1 : 1) : 0;
        previousCategory = supported ? category : null;
        var reading = new AdaptiveDetector.Reading(rms, lastReading.baseline(), lastReading.threshold(), lastReading.calibrated(), lastReading.triggered());
        var event = alerts.event(started.plusNanos(Math.round(audioSeconds() * 1e9)), source + " [periodic]", audioSeconds(), reading, prediction);
        if (!event.isAlert()) return Optional.of(event);
        SoundEvent.Status status = SoundEvent.Status.ALERT;
        if (supportedStreak < (category == Category.EXPLOSION ? 2 : 1)) status = SoundEvent.Status.EVIDENCE_SUPPRESSED;
        else {
            if (totalSamples - lastSupported[category.ordinal()] < 48000) status = SoundEvent.Status.DUPLICATE_SUPPRESSED;
            lastSupported[category.ordinal()] = totalSamples;
        }
        if (status == SoundEvent.Status.ALERT) return Optional.of(event);
        return Optional.of(new SoundEvent(event.id(), event.timestamp(), event.source(), event.audioSeconds(), event.rms(), event.baseline(), event.threshold(),
                event.category(), event.voteShare(), event.nearestDistance(), omniear.alerts.Priority.NONE, status));
    }

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
