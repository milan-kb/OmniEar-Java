package omniear.audio;

import java.util.Arrays;

/** Shared training/inference sample operations. No per-clip gain normalization. */
public final class AudioSamples {
    public static final int SAMPLE_RATE = 16000;
    public static final int BLOCK_SIZE = 1600;
    public static final int WINDOW_SIZE = 40000;
    private AudioSamples() { }

    public static double rms(float[] samples) {
        if (samples.length == 0) throw new IllegalArgumentException("Empty audio");
        double sum = 0;
        for (float sample : samples) {
            if (!Float.isFinite(sample)) throw new IllegalArgumentException("Nonfinite audio sample");
            sum += (double) sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }

    /** Port of upstream extract_loudest_window: rolling sum of 100 ms RMS frames. */
    public static float[] loudestWindow(float[] samples, int size, int rate) {
        if (size < 1 || rate < 1) throw new IllegalArgumentException("Invalid window size/rate");
        if (samples.length <= size) {
            float[] result = new float[size];
            System.arraycopy(samples, 0, result, (size - samples.length) / 2, samples.length);
            return result;
        }
        int frameSize = Math.max(1, rate / 10);
        int frames = samples.length / frameSize;
        int windowFrames = Math.max(1, size / frameSize);
        double[] prefix = new double[frames + 1];
        for (int i = 0; i < frames; i++) {
            double sum = 0;
            for (int j = i * frameSize; j < (i + 1) * frameSize; j++) sum += (double) samples[j] * samples[j];
            prefix[i + 1] = prefix[i] + Math.sqrt(sum / frameSize);
        }
        int center = frames / 2;
        if (frames > windowFrames) {
            int best = 0;
            for (int i = 1; i <= frames - windowFrames; i++) {
                if (prefix[i + windowFrames] - prefix[i] > prefix[best + windowFrames] - prefix[best]) best = i;
            }
            center = best + windowFrames / 2;
        }
        int start = Math.max(0, Math.min(samples.length - size, center * frameSize - size / 2));
        return Arrays.copyOfRange(samples, start, start + size);
    }
}
