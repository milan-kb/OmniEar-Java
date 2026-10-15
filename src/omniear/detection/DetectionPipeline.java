package omniear.detection;

import java.time.Instant;
import java.util.*;
import omniear.audio.AudioSamples;
import omniear.alerts.AlertPolicy;
import omniear.alerts.Priority;
import omniear.model.*;

/** Bounded hybrid: aligned 2.5 s windows, periodic coverage and onset-tail captures. */
public final class DetectionPipeline {
    private static final int STRIDE = 20000, MIN_SPACING = 8000, MIN_CAPTURE = 4000;
    // One extra block retains the onset when a read straddles its sample deadline.
    private final float[] ring = new float[AudioSamples.WINDOW_SIZE + AudioSamples.BLOCK_SIZE];
    private final AdaptiveDetector detector;
    private final SoundClassifier classifier;
    private final FeatureExtractor extractor = new FeatureExtractor();
    private final AlertPolicy alerts = new AlertPolicy();
    private final Instant started;
    private final String source;
    // ponytail: merge same-class support within 5 s; annotated offsets are needed
    // to separate distinct, closely spaced instances rather than weak episodes.
    private final double[] supportedAt = new double[Category.values().length];
    private int ringSize, ringPosition;
    private long totalSamples, lastAnalysis, nextPeriodic = AudioSamples.WINDOW_SIZE, onsetDue = -1;
    private double onsetSeconds;
    private AdaptiveDetector.Reading lastReading;
    private long analyses, quietWindows, onsets;
    private final EnumMap<SoundEvent.Status, Long> outcomes = new EnumMap<>(SoundEvent.Status.class);

    public DetectionPipeline(AdaptiveDetector.Config config, SoundClassifier classifier, Instant started, String source) {
        detector = new AdaptiveDetector(config);
        this.classifier = classifier;
        this.started = Objects.requireNonNull(started);
        this.source = Objects.requireNonNull(source);
        Arrays.fill(supportedAt, Double.NEGATIVE_INFINITY);
    }
    public AdaptiveDetector.Reading lastReading() { return lastReading; }
    public double audioSeconds() { return totalSamples / (double) AudioSamples.SAMPLE_RATE; }
    public String diagnostics() {
        return "Hybrid analysis: onsets=" + onsets + ", windows=" + analyses + ", silence skipped=" + quietWindows + ", outcomes=" + outcomes;
    }

    /** Explicit clip inspection; it uses exactly the same feature transform and alert policy. */
    public static SoundEvent classifyClip(float[] audio, SoundClassifier classifier, Instant timestamp, String source) {
        var reading = new AdaptiveDetector.Reading(AudioSamples.rms(audio), 0, 0, false, false);
        var prediction = classifier == null ? null : classifier.classify(new FeatureExtractor().extract(audio));
        return new AlertPolicy().event(timestamp, source + " [clip; Stage 1 bypass]", 0, reading, prediction);
    }

    public Optional<SoundEvent> accept(float[] block) {
        if (block.length == 0 || block.length > AudioSamples.BLOCK_SIZE) throw new IllegalArgumentException("Expected up to 100 ms of audio");
        double blockTime = audioSeconds();
        lastReading = detector.accept(block, blockTime);
        if (lastReading.triggered()) onsets++;
        for (float value : block) {
            ring[ringPosition] = value;
            ringPosition = (ringPosition + 1) % ring.length;
            ringSize = Math.min(ring.length, ringSize + 1);
        }
        totalSamples += block.length;
        if (lastReading.triggered() && onsetDue < 0) {
            onsetSeconds = blockTime;
            onsetDue = totalSamples + AudioSamples.WINDOW_SIZE - block.length;
        }
        // An onset gets a full training-length tail rather than a periodic window
        // dominated by its preceding silence. A pending onset has a fixed deadline;
        // later onsets cannot postpone it indefinitely.
        if (onsetDue >= 0 ? totalSamples >= onsetDue : totalSamples >= nextPeriodic) {
            if (totalSamples - lastAnalysis >= MIN_SPACING) return analyze();
        }
        return Optional.empty();
    }

    /** EOF includes short startup sounds and unexamined tails; explicit Stop never flushes. */
    public Optional<SoundEvent> finish() {
        return totalSamples > lastAnalysis && ringSize >= MIN_CAPTURE ? analyze() : Optional.empty();
    }

    private Optional<SoundEvent> analyze() {
        lastAnalysis = totalSamples;
        while (nextPeriodic <= totalSamples) nextPeriodic += STRIDE;
        boolean onset = onsetDue >= 0;
        long start = onset ? Math.max(totalSamples - ringSize, Math.round(onsetSeconds * AudioSamples.SAMPLE_RATE))
                : Math.max(0, totalSamples - AudioSamples.WINDOW_SIZE);
        double seconds = start / (double) AudioSamples.SAMPLE_RATE;
        if (onset) onsetDue = -1;
        int size = (int) Math.min(AudioSamples.WINDOW_SIZE, totalSamples - start);
        float[] captured = new float[size];
        int first = (ringPosition - (int) (totalSamples - start) + ring.length) % ring.length;
        for (int i = 0; i < size; i++) captured[i] = ring[(first + i) % ring.length];
        // Do not amplify digital silence into an arbitrary nearest-neighbor class.
        double rms = AudioSamples.rms(captured);
        if (rms < .0001) { quietWindows++; return Optional.empty(); }
        analyses++;
        var reading = new AdaptiveDetector.Reading(rms, lastReading.baseline(), lastReading.threshold(), lastReading.calibrated(), onset);
        var prediction = classifier == null ? null : classifier.classify(extractor.extract(captured));
        var event = alerts.event(started.plusNanos(Math.round(seconds * 1_000_000_000)), source, seconds, reading, prediction);
        if (event.isAlert()) {
            int index = event.category().ordinal();
            boolean duplicate = audioSeconds() - supportedAt[index] <= 5;
            supportedAt[index] = audioSeconds();
            if (duplicate) event = new SoundEvent(event.id(), event.timestamp(), event.source(), event.audioSeconds(),
                    event.rms(), event.baseline(), event.threshold(), event.category(), event.voteShare(),
                    event.nearestDistance(), Priority.NONE, SoundEvent.Status.DUPLICATE_SUPPRESSED);
        }
        outcomes.merge(event.status(), 1L, Long::sum);
        return Optional.of(event);
    }
}
