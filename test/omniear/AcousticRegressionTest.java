package omniear;

import java.util.*;
import java.time.Instant;
import omniear.detection.*;
import omniear.model.*;
import omniear.alerts.*;

/** DSP and scheduling mechanics only; real-recording evidence is evaluated separately. */
public final class AcousticRegressionTest {
    private AcousticRegressionTest() { }
    public static void main(String[] args) throws Exception {
        fftAndMfcc();
        float[] tone = new float[40000], quiet = new float[40000];
        for (int i = 0; i < tone.length; i++) {
            tone[i] = (float) (.4 * Math.sin(2 * Math.PI * 750 * i / 16000));
            quiet[i] = tone[i] / 16;
        }
        var extractor = new FeatureExtractor();
        double[] a = extractor.extract(tone).values(), b = extractor.extract(quiet).values();
        for (int i = 0; i < a.length; i++)
            TestSuite.check(Math.abs(a[i] - b[i]) < 1e-5, "gain-invariant training/inference feature " + i);
        TestSuite.check(Arrays.stream(extractor.extract(new float[40000]).values()).allMatch(Double::isFinite), "finite silence after normalization");
        startupAndSustained();
        boundedAndSilent();
        straddledWindow();
        evidenceAndHistory();
    }

    private static void startupAndSustained() {
        var detector = new AdaptiveDetector(AdaptiveDetector.Config.defaults());
        TestSuite.check(detector.accept(toneBlock(), 0).triggered(), "startup onset is not blinded by calibration");
        SoundClassifier supported = features -> new SoundClassifier.Prediction(Category.SIREN_TRAFFIC, 1, 0);
        var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), supported, Instant.EPOCH, "mechanics");
        var events = new ArrayList<SoundEvent>();
        for (int i = 0; i < 100; i++) pipeline.accept(toneBlock()).ifPresent(events::add);
        pipeline.finish().ifPresent(events::add);
        TestSuite.check(!events.isEmpty() && events.getFirst().category() == Category.SIREN_TRAFFIC, "sustained startup sound is analyzed");
        TestSuite.check(events.stream().filter(SoundEvent::isAlert).count() == 1, "one supported episode does not repeat alerts");
        pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), supported, Instant.EPOCH, "short");
        for (int i = 0; i < 8; i++) pipeline.accept(toneBlock());
        TestSuite.check(pipeline.finish().isPresent(), "short startup WAV flushes at EOF");
        TestSuite.check(pipeline.finish().isEmpty(), "EOF cannot classify twice");
    }

    private static float[] toneBlock() {
        float[] block = new float[1600];
        for (int i = 0; i < block.length; i++) block[i] = (float) (.2 * Math.sin(2 * Math.PI * 750 * i / 16000));
        return block;
    }

    private static void boundedAndSilent() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        SoundClassifier model = features -> {
            calls.incrementAndGet();
            return new SoundClassifier.Prediction(Category.BACKGROUND, 1, 0);
        };
        var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), model, Instant.EPOCH, "silence");
        for (int i = 0; i < 200; i++) TestSuite.check(pipeline.accept(new float[1600]).isEmpty(), "silence does not flood events");
        TestSuite.check(pipeline.finish().isEmpty() && calls.get() == 0, "silence does not invoke ML or get normalized into a class");
        pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), model, Instant.EPOCH, "gain changes");
        for (int i = 0; i < 200; i++) {
            float[] block = toneBlock();
            for (int j = 0; j < block.length; j++) block[j] = (float) Math.clamp(block[j] * (i % 20 < 10 ? .25 : 10), -1, 1);
            pipeline.accept(block);
        }
        pipeline.finish();
        TestSuite.check(calls.get() > 5 && calls.get() <= 40, "onsets plus periodic analysis stay below 2 Hz including gain/clipping changes");
    }

    private static void fftAndMfcc() throws Exception {
        var fft = FeatureExtractor.class.getDeclaredMethod("fft", double[].class, double[].class);
        fft.setAccessible(true);
        var random = new Random(17);
        double[] input = new double[512];
        for (int i = 0; i < input.length; i++) input[i] = random.nextDouble() - .5;
        double[] real = input.clone(), imaginary = new double[512], refReal = new double[512], refImag = new double[512];
        fft.invoke(null, real, imaginary);
        for (int k = 0; k < 512; k++) for (int n = 0; n < 512; n++) {
            refReal[k] += input[n] * Math.cos(-2 * Math.PI * k * n / 512);
            refImag[k] += input[n] * Math.sin(-2 * Math.PI * k * n / 512);
        }
        double error = 0;
        for (int k = 0; k < 512; k++) error = Math.max(error, Math.hypot(real[k] - refReal[k], imaginary[k] - refImag[k]));
        TestSuite.check(error < 1e-9, "radix-2 FFT agrees with independent DFT, all complex bins");

        float[] frame = toneBlock();
        double mean = 0;
        for (int i = 0; i < 400; i++) mean += frame[i] / 400.;
        double[] power = new double[257];
        for (int k = 0; k < power.length; k++) {
            double re = 0, im = 0;
            for (int n = 0; n < 400; n++) {
                double value = (frame[n] - mean) * (.5 - .5 * Math.cos(2 * Math.PI * n / 399));
                re += value * Math.cos(-2 * Math.PI * k * n / 512);
                im += value * Math.sin(-2 * Math.PI * k * n / 512);
            }
            power[k] = (re * re + im * im) / 512;
        }
        double[] edges = new double[28], mel = new double[26];
        for (int i = 0; i < edges.length; i++) edges[i] = 700 * (Math.pow(1 + 8000. / 700, i / 27.) - 1);
        for (int band = 0; band < 26; band++) {
            double energy = 0;
            for (int bin = 0; bin < 257; bin++) {
                double hz = bin * 16000. / 512;
                double triangle = Math.max(0, Math.min((hz - edges[band]) / (edges[band + 1] - edges[band]),
                        (edges[band + 2] - hz) / (edges[band + 2] - edges[band + 1])));
                energy += triangle * power[bin];
            }
            mel[band] = Math.log(Math.max(1e-12, energy));
        }
        var method = FeatureExtractor.class.getDeclaredMethod("frame", float[].class, int.class, double[].class);
        method.setAccessible(true);
        double[] actual = (double[]) method.invoke(new FeatureExtractor(), frame, 0, new double[257]);
        error = 0;
        for (int c = 0; c < 13; c++) {
            double expected = 0;
            for (int band = 0; band < 26; band++) expected += mel[band] * Math.cos(Math.PI * c * (band + .5) / 26);
            expected *= Math.sqrt((c == 0 ? 1. : 2.) / 26);
            error = Math.max(error, Math.abs(expected - actual[7 + c]));
        }
        TestSuite.check(error < 1e-7, "all MFCCs agree with independent DFT, triangular mel filters and orthonormal DCT-II");
    }

    private static void straddledWindow() {
        float[] audio = new float[42000];
        var random = new Random(39);
        for (int i = 0; i < audio.length; i++) audio[i] = (float) (.1 + .2 * random.nextDouble());
        var actual = new ArrayList<FeatureVector>();
        SoundClassifier recorder = features -> {
            actual.add(features);
            return new SoundClassifier.Prediction(Category.BACKGROUND, 1, 0);
        };
        var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), recorder, Instant.EPOCH, "irregular reads");
        for (int i = 0; i < audio.length; i += 1103)
            pipeline.accept(Arrays.copyOfRange(audio, i, Math.min(audio.length, i + 1103)));
        double[] expected = new FeatureExtractor().extract(Arrays.copyOf(audio, 40000)).values();
        TestSuite.check(actual.size() == 1 && Arrays.equals(expected, actual.getFirst().values()), "read straddling onset deadline retains exactly the aligned 40000 samples");
    }

    private static void evidenceAndHistory() throws Exception {
        var policy = new AlertPolicy();
        var reading = new AdaptiveDetector.Reading(.1, .01, .06, true, true);
        double[] radii = {1.5, .3, 1, .75};
        var persisted = new ArrayList<SoundEvent>();
        for (Category category : Category.values()) {
            if (category == Category.BACKGROUND) continue;
            double radius = radii[category.ordinal()];
            var supported = policy.event(Instant.EPOCH, "geometry mechanics", 0, reading,
                    new SoundClassifier.Prediction(category, .1, radius, radius, 10));
            TestSuite.check(supported.isAlert() && supported.priority() == category.priority(), "inclusive measured radius accepts support even below historical vote gate");
            persisted.add(supported);
            var distant = policy.event(Instant.EPOCH, "geometry mechanics", 0, reading,
                    new SoundClassifier.Prediction(category, 1, radius + .001, radius + .001, 10));
            TestSuite.check(distant.status() == SoundEvent.Status.DISTANCE_SUPPRESSED, "unanimous out-of-distribution vote is rejected");
            persisted.add(distant);
            var conflict = policy.event(Instant.EPOCH, "geometry mechanics", 0, reading,
                    new SoundClassifier.Prediction(category, 1, .1, .1, .1));
            TestSuite.check(conflict.status() == SoundEvent.Status.BACKGROUND_CONFLICT_SUPPRESSED, "near background conflict cannot become a high-vote alert");
            persisted.add(conflict);
        }
        var root = java.nio.file.Files.createTempDirectory("omniear-evidence-history-");
        try {
            var csv = root.resolve("events.csv");
            try (var store = new EventStore(csv)) { for (var event : persisted) store.append(event); }
            try (var store = new EventStore(csv)) { TestSuite.check(store.history().equals(persisted), "new evidence outcomes and low-vote alerts round-trip without changing CSV columns"); }
        } finally { TestSuite.deleteTree(root); }
    }
}
