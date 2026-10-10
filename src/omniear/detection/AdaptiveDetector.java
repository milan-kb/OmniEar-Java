package omniear.detection;

import java.util.ArrayDeque;
import omniear.audio.AudioSamples;

/** Single-worker detector. Current block enters the baseline AFTER thresholding. */
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
    private double sum;
    private double lastTrigger = Double.NEGATIVE_INFINITY;
    private double lastTime = Double.NEGATIVE_INFINITY;

    public AdaptiveDetector(Config config) { this.config = java.util.Objects.requireNonNull(config); }

    public Reading accept(float[] samples, double audioSeconds) {
        if (!Double.isFinite(audioSeconds) || audioSeconds < 0 || audioSeconds < lastTime) {
            throw new IllegalArgumentException("Audio clock must be finite and monotonic");
        }
        lastTime = audioSeconds;
        double energy = AudioSamples.rms(samples);
        double baseline = history.isEmpty() ? 0 : Math.max(0, sum / history.size());
        double threshold = Math.max(baseline * config.multiplier(), config.floor());
        boolean calibrated = history.size() >= config.warmupBlocks();
        boolean triggered = calibrated && energy > threshold && audioSeconds - lastTrigger > config.cooldownSeconds();
        if (triggered) lastTrigger = audioSeconds;
        if (history.size() == config.baselineBlocks()) sum -= history.removeFirst();
        history.addLast(energy);
        sum += energy;
        return new Reading(energy, baseline, threshold, calibrated, triggered);
    }
}
