package omniear.model;

import omniear.audio.AudioSamples;

/** Java DSP, NOT YAMNet: Hann-windowed FFT, spectral features and 13 MFCCs. */
public final class FeatureExtractor {
    private static final int FRAME = 400, HOP = 160, FFT = 512, BANDS = 26;
    private static final int FRAME_FEATURES = 20;
    public static final int DIMENSIONS = FRAME_FEATURES * 3;
    private final int windowSize;
    private final boolean normalize;
    private final double[][] melWeights = new double[BANDS][FFT / 2 + 1];

    public FeatureExtractor() {
        this(AudioSamples.SAMPLE_RATE, true);
    }

    /** Explicit experimental transform; training and inference must use the same instance settings. */
    public FeatureExtractor(int windowSize, boolean normalize) {
        if (windowSize < FRAME || windowSize > 40000) throw new IllegalArgumentException("Feature window must be 25 ms–2.5 s");
        this.windowSize = windowSize;
        this.normalize = normalize;
        double topMel = 2595 * Math.log10(1 + 8000.0 / 700);
        double[] edges = new double[BANDS + 2];
        for (int i = 0; i < edges.length; i++) edges[i] = 700 * (Math.pow(10, topMel * i / (BANDS + 1) / 2595) - 1);
        for (int band = 0; band < BANDS; band++) for (int bin = 0; bin <= FFT / 2; bin++) {
            double hz = bin * 16000.0 / FFT;
            melWeights[band][bin] = Math.max(0, Math.min((hz - edges[band]) / (edges[band + 1] - edges[band]),
                    (edges[band + 2] - hz) / (edges[band + 2] - edges[band + 1])));
        }
    }

    public FeatureVector extract(float[] audio) {
        AudioSamples.rms(audio); // Validate once at the boundary, including NaNs.
        float[] window = AudioSamples.loudestWindow(audio, windowSize, AudioSamples.SAMPLE_RATE);
        if (normalize) {
            double rms = AudioSamples.rms(window);
            if (rms > 1e-6) for (int i = 0; i < window.length; i++) window[i] *= (float) (.1 / rms);
        }
        double[] sum = new double[FRAME_FEATURES], squares = new double[FRAME_FEATURES], maxima = new double[FRAME_FEATURES];
        java.util.Arrays.fill(maxima, Double.NEGATIVE_INFINITY);
        double[] previous = new double[FFT / 2 + 1];
        int frames = 0;
        for (int start = 0; start + FRAME <= window.length; start += HOP) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Feature extraction cancelled");
            double[] values = frame(window, start, previous);
            for (int i = 0; i < FRAME_FEATURES; i++) {
                sum[i] += values[i]; squares[i] += values[i] * values[i]; maxima[i] = Math.max(maxima[i], values[i]);
            }
            frames++;
        }
        double[] result = new double[DIMENSIONS];
        for (int i = 0; i < FRAME_FEATURES; i++) {
            double mean = sum[i] / frames;
            result[i] = mean;
            result[i + FRAME_FEATURES] = maxima[i];
            result[i + FRAME_FEATURES * 2] = Math.sqrt(Math.max(0, squares[i] / frames - mean * mean));
        }
        return new FeatureVector(result);
    }

    private double[] frame(float[] audio, int start, double[] previous) {
        double energy = 0, mean = 0;
        int crossings = 0;
        for (int i = 0; i < FRAME; i++) {
            float value = audio[start + i];
            mean += value;
            energy += (double) value * value;
            if (i > 0 && (value >= 0) != (audio[start + i - 1] >= 0)) crossings++;
        }
        mean /= FRAME;
        double[] real = new double[FFT], imaginary = new double[FFT];
        for (int i = 0; i < FRAME; i++) real[i] = (audio[start + i] - mean) * (.5 - .5 * Math.cos(2 * Math.PI * i / (FRAME - 1)));
        fft(real, imaginary);
        double[] power = new double[FFT / 2 + 1];
        double total = 0, weighted = 0, logs = 0;
        for (int i = 0; i < power.length; i++) {
            power[i] = (real[i] * real[i] + imaginary[i] * imaginary[i]) / FFT;
            total += power[i]; weighted += i * power[i]; logs += Math.log(Math.max(1e-12, power[i]));
        }
        double centroid = total < 1e-12 ? 0 : weighted / total;
        double bandwidth = 0, cumulative = 0, flux = 0;
        int rolloff = 0;
        boolean reached = false;
        for (int i = 0; i < power.length; i++) {
            bandwidth += power[i] * (i - centroid) * (i - centroid);
            cumulative += power[i];
            if (!reached && cumulative >= .85 * total) { rolloff = i; reached = true; }
            double normalized = total < 1e-12 ? 0 : power[i] / total;
            flux += Math.pow(Math.max(0, normalized - previous[i]), 2);
            previous[i] = normalized;
        }
        double[] features = new double[FRAME_FEATURES];
        features[0] = Math.sqrt(energy / FRAME);
        features[1] = crossings / (double) (FRAME - 1);
        features[2] = centroid / (FFT / 2);
        features[3] = total < 1e-12 ? 0 : Math.sqrt(bandwidth / total) / (FFT / 2);
        features[4] = rolloff / (double) (FFT / 2);
        features[5] = total < 1e-12 ? 0 : Math.min(1, Math.exp(logs / power.length) / (total / power.length));
        features[6] = Math.sqrt(flux);
        double[] mel = new double[BANDS];
        for (int band = 0; band < BANDS; band++) {
            double value = 0;
            for (int bin = 0; bin < power.length; bin++) value += melWeights[band][bin] * power[bin];
            mel[band] = Math.log(Math.max(1e-12, value));
        }
        for (int coefficient = 0; coefficient < 13; coefficient++) {
            double value = 0;
            for (int band = 0; band < BANDS; band++) value += mel[band] * Math.cos(Math.PI * coefficient * (band + .5) / BANDS);
            features[7 + coefficient] = value * Math.sqrt((coefficient == 0 ? 1.0 : 2.0) / BANDS);
        }
        return features;
    }

    private static void fft(double[] real, double[] imaginary) {
        for (int i = 1, j = 0; i < FFT; i++) {
            int bit = FFT >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = real[i]; real[i] = real[j]; real[j] = t; }
        }
        for (int length = 2; length <= FFT; length <<= 1) {
            double angle = -2 * Math.PI / length;
            for (int start = 0; start < FFT; start += length) {
                double wr = 1, wi = 0;
                for (int j = 0; j < length / 2; j++) {
                    int a = start + j, b = a + length / 2;
                    double tr = wr * real[b] - wi * imaginary[b], ti = wr * imaginary[b] + wi * real[b];
                    real[b] = real[a] - tr; imaginary[b] = imaginary[a] - ti;
                    real[a] += tr; imaginary[a] += ti;
                    double next = wr * Math.cos(angle) - wi * Math.sin(angle);
                    wi = wr * Math.sin(angle) + wi * Math.cos(angle); wr = next;
                }
            }
        }
    }
}
