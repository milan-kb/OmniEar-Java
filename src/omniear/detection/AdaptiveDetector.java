package omniear.detection;

import java.util.ArrayDeque;
import omniear.audio.AudioSamples;

/** Single-worker onset hint; calibration estimates noise but never disables analysis. */
public final class AdaptiveDetector {
    public record Config(int baselineBlocks, int warmupBlocks, double multiplier, double floor, double cooldownSeconds) {
        public Config {
            if (baselineBlocks < 1 || warmupBlocks < 1 || warmupBlocks > baselineBlocks
                    || !Double.isFinite(multiplier) || multiplier <= 0
                    || !Double.isFinite(floor) || floor <= 0 || floor > 1
                    || !Double.isFinite(cooldownSeconds) || cooldownSeconds < 0) {
                throw new IllegalArgumentException("Invalid detector calibration");
            }
        }
        public static Config defaults() { return new Config(50, 20, 6, .02, 1.5); }
    }

    public record Reading(double rms, double baseline, double threshold, boolean calibrated, boolean triggered) { }
    private final Config config;
    private final ArrayDeque<Double> history = new ArrayDeque<>();
    private double lastTrigger = Double.NEGATIVE_INFINITY;
    private double lastTime = Double.NEGATIVE_INFINITY;

    public AdaptiveDetector(Config config) { this.config = java.util.Objects.requireNonNull(config); }

    public Reading accept(float[] samples, double audioSeconds) {
        if (!Double.isFinite(audioSeconds) || audioSeconds < 0 || audioSeconds < lastTime) {
            throw new IllegalArgumentException("Audio clock must be finite and monotonic");
        }
        lastTime = audioSeconds;
        double energy = AudioSamples.rms(samples);
        // Lower quantile resists isolated foreground energy; sustained events still
        // need the pipeline's periodic path, not an ever-more-sensitive RMS gate.
        double[] levels = history.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double baseline = levels.length == 0 ? 0 : levels[(levels.length - 1) / 5];
        double threshold = Math.max(baseline * config.multiplier(), config.floor());
        boolean calibrated = history.size() >= config.warmupBlocks();
        boolean triggered = energy > threshold && audioSeconds - lastTrigger > config.cooldownSeconds();
        if (triggered) lastTrigger = audioSeconds;
        if (history.size() == config.baselineBlocks()) history.removeFirst();
        history.addLast(energy);
        return new Reading(energy, baseline, threshold, calibrated, triggered);
    }
}
