package omniear;

import omniear.audio.AudioSamples;
import omniear.detection.AdaptiveDetector;
import omniear.audio.WavSource;
import omniear.audio.AudioSource;
import java.io.*;
import java.nio.file.*;
import javax.sound.sampled.*;
import omniear.model.*;
import omniear.alerts.*;
import java.time.Instant;
import omniear.detection.*;
import java.util.concurrent.*;
import javax.swing.SwingUtilities;
import omniear.ui.*;

/** Small executable regression suite; synthetic signals are test fixtures, not training data. */
public final class TestSuite {
    private static int checks;
    private TestSuite() { }

    public static void main(String[] args) throws Exception {
        detector();
        windowing();
        audio();
        featuresAndClassifier();
        trainingAndPersistence();
        heldOutEvaluation();
        pipelineAndThreading();
        LivePipelineTest.main(new String[0]);
        integration();
        TrainedSmokeTest.main(new String[0]);
        System.out.println("PASS: " + checks + " checks");
    }

    private static void detector() {
        var detector = new AdaptiveDetector(AdaptiveDetector.Config.defaults());
        for (int i = 0; i < 20; i++) check(!detector.accept(new float[1600], i * .1).triggered(), "warmup");
        var spike = detector.accept(constant(1600, .03f), 2);
        check(spike.triggered(), "absolute floor spike");
        check(spike.baseline() == 0 && spike.threshold() == .02, "baseline before current block");
        check(!detector.accept(constant(1600, .5f), 3.5).triggered(), "strict cooldown boundary");
        check(detector.accept(constant(1600, .5f), 3.6).triggered(), "cooldown expires");
        detector = new AdaptiveDetector(AdaptiveDetector.Config.defaults());
        for (int i = 0; i < 50; i++) detector.accept(constant(1600, .01f), i * .1);
        check(!detector.accept(constant(1600, .05f), 5).triggered(), "adaptive threshold");
        for (int i = 1; i <= 50; i++) detector.accept(new float[1600], 5 + i * .1);
        check(detector.accept(constant(1600, .03f), 10.1).triggered(), "rolling eviction restores floor");
        var equality = new AdaptiveDetector(new AdaptiveDetector.Config(50, 20, 6, .125, 1.5));
        for (int i = 0; i < 20; i++) equality.accept(new float[1600], i * .1);
        check(!equality.accept(constant(1600, .125f), 2).triggered(), "strict threshold equality is not a spike");
        expect(IllegalArgumentException.class, () -> detectorConfig(0), "invalid calibration");
        check(Math.abs(AudioSamples.rms(new float[]{-1, 1}) - 1) < 1e-12, "normalized RMS");
        expect(IllegalArgumentException.class, () -> AudioSamples.rms(new float[]{Float.NaN}), "nonfinite samples");
    }

    private static void detectorConfig(double multiplier) {
        new AdaptiveDetector.Config(50, 20, multiplier, .02, 1.5);
    }

    private static void windowing() {
        float[] input = new float[8000];
        java.util.Arrays.fill(input, 6100, 6200, 1);
        float[] selected = AudioSamples.loudestWindow(input, 2000, 1000);
        check(selected.length == 2000 && AudioSamples.rms(selected) > .2, "loudest transient");
        check(java.util.Arrays.equals(AudioSamples.loudestWindow(new float[]{1, 2}, 5, 1000),
                new float[]{0, 1, 2, 0, 0}), "centered padding");
    }

    private static void audio() throws Exception {
        Path directory = Files.createTempDirectory("omniear-audio-test-");
        try {
            Path stereo = directory.resolve("stereo.wav");
            writeWav(stereo, new AudioFormat(48000, 16, 2, true, false), .5, 1000, .5);
            float[] mono = WavSource.load(stereo);
            check(mono.length == 8000, "48 kHz resampled duration");
            check(Math.abs(AudioSamples.rms(mono) - .5 / Math.sqrt(2)) < .002, "PCM amplitude normalization");
            Path aliased = directory.resolve("alias.wav");
            writeWav(aliased, new AudioFormat(48000, 16, 1, true, false), .5, 12000, .5);
            check(AudioSamples.rms(WavSource.load(aliased)) < .01, "anti-aliasing above output Nyquist");
            Path eight = directory.resolve("eight.wav");
            writeWav(eight, new AudioFormat(16000, 8, 1, false, false), .2, 500, .5);
            check(Math.abs(AudioSamples.rms(WavSource.load(eight)) - .5 / Math.sqrt(2)) < .01, "unsigned PCM8");
            for (int bits : new int[]{24, 32}) {
                Path pcm = directory.resolve("pcm" + bits + ".wav");
                writeWav(pcm, new AudioFormat(16000, bits, 1, true, false), .2, 500, .5);
                check(Math.abs(AudioSamples.rms(WavSource.load(pcm)) - .5 / Math.sqrt(2)) < .001, "signed PCM" + bits);
            }
            for (int bits : new int[]{32, 64}) {
                Path floating = directory.resolve("float" + bits + ".wav");
                var format = new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, 16000, bits, 1, bits / 8, 16000, false);
                writeWav(floating, format, .2, 500, .5);
                check(Math.abs(AudioSamples.rms(WavSource.load(floating)) - .5 / Math.sqrt(2)) < .001, "float PCM" + bits);
                writeWav(floating, format, .2, 500, Double.NaN);
                expect(IOException.class, () -> WavSource.load(floating), "nonfinite float PCM rejected");
            }
            for (int rate : new int[]{8000, 44100, 96000}) {
                Path converted = directory.resolve("rate" + rate + ".wav");
                writeWav(converted, new AudioFormat(rate, 16, 1, true, false), .5, 1000, .5);
                float[] normalized = WavSource.load(converted);
                check(normalized.length == 8000, "resampled length at " + rate);
                check(Math.abs(AudioSamples.rms(normalized) - .5 / Math.sqrt(2)) < .002, "resampled amplitude at " + rate);
            }
            Path cancellingStereo = directory.resolve("opposite-channels.wav");
            byte[] stereoBytes = new byte[3200 * 4];
            for (int i = 0; i < 3200; i++) { stereoBytes[4 * i + 1] = 32; stereoBytes[4 * i + 3] = -32; }
            try (var stream = new AudioInputStream(new ByteArrayInputStream(stereoBytes), new AudioFormat(16000, 16, 2, true, false), 3200)) {
                AudioSystem.write(stream, AudioFileFormat.Type.WAVE, cancellingStereo.toFile());
            }
            check(AudioSamples.rms(WavSource.load(cancellingStereo)) == 0, "stereo downmix averages both channels");
            Path truncated = directory.resolve("truncated.wav");
            byte[] complete = Files.readAllBytes(stereo);
            Files.write(truncated, java.util.Arrays.copyOf(complete, complete.length - 100));
            expect(IOException.class, () -> WavSource.load(truncated), "truncated PCM rejected");
            Path bad = directory.resolve("bad.wav");
            Files.writeString(bad, "not audio");
            expect(IOException.class, () -> WavSource.load(bad), "invalid audio");
            expect(IOException.class, () -> WavSource.load(directory.resolve("missing.wav")), "missing audio");
            var source = new WavSource(stereo);
            source.close();
            expect(IOException.class, source::open, "closed source cannot reopen");
        } finally { deleteTree(directory); }
    }

    public static void writeWav(Path path, AudioFormat format, double seconds, double frequency, double amplitude) throws IOException {
        int frames = (int) (format.getSampleRate() * seconds);
        int width = format.getSampleSizeInBits() / 8;
        byte[] bytes = new byte[frames * format.getFrameSize()];
        for (int i = 0; i < frames; i++) {
            double sample = amplitude * Math.sin(2 * Math.PI * frequency * i / format.getSampleRate());
            long raw;
            if (format.getEncoding().equals(AudioFormat.Encoding.PCM_FLOAT)) {
                raw = width == 4 ? Integer.toUnsignedLong(Float.floatToIntBits((float) sample)) : Double.doubleToLongBits(sample);
            } else {
                raw = Math.round(sample * (1L << (format.getSampleSizeInBits() - 1)));
                if (format.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED)) raw += 1L << (format.getSampleSizeInBits() - 1);
            }
            for (int c = 0; c < format.getChannels(); c++) for (int b = 0; b < width; b++) {
                int offset = format.isBigEndian() ? width - 1 - b : b;
                bytes[i * format.getFrameSize() + c * width + offset] = (byte) (raw >> (8 * b));
            }
        }
        try (var stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile());
        }
    }

    private static void featuresAndClassifier() throws Exception {
        var extractor = new FeatureExtractor(40000, false);
        var silence = extractor.extract(new float[40000]);
        check(silence.size() == FeatureExtractor.DIMENSIONS, "feature dimensions");
        for (double value : silence.values()) check(Double.isFinite(value), "finite silence features");
        float[] tone = new float[40000];
        for (int i = 0; i < tone.length; i++) tone[i] = (float) (.5 * Math.sin(2 * Math.PI * 1000 * i / 16000));
        var features = extractor.extract(tone);
        check(Math.abs(features.get(0) - .5 / Math.sqrt(2)) < .001, "frame RMS");
        check(Math.abs(features.get(2) * 8000 - 1000) < 20, "spectral centroid");
        check(features.get(5) < .01, "tone spectral flatness");
        var streamExtractor = new FeatureExtractor(16000, true);
        var normalized = streamExtractor.extract(tone);
        float[] quietTone = tone.clone();
        for (int i = 0; i < quietTone.length; i++) quietTone[i] *= .25f;
        var quietFeatures = streamExtractor.extract(quietTone);
        double deviation = 0;
        for (int i = 0; i < normalized.size(); i++) deviation = Math.max(deviation, Math.abs(normalized.get(i) - quietFeatures.get(i)));
        check(deviation < 1e-5 && Math.abs(normalized.get(0) - .1) < .001, "shared one-second transform is invariant to unclipped recording gain");
        check(Math.abs(AudioSamples.rms(tone) - .5 / Math.sqrt(2)) < .001, "feature normalization preserves source samples");
        expect(IllegalArgumentException.class, () -> extractor.extract(new float[]{Float.NaN}), "invalid feature input");
        var examples = new java.util.ArrayList<TrainingExample>();
        for (Category category : Category.values()) {
            double[] vector = new double[FeatureExtractor.DIMENSIONS];
            java.util.Arrays.fill(vector, category.ordinal());
            examples.add(new TrainingExample(category, new FeatureVector(vector), "test-only mechanics fixture"));
        }
        var classifier = new KnnClassifier(examples, 3);
        for (TrainingExample example : examples) {
            var prediction = classifier.classify(example.features());
            check(prediction.category() == example.category() && prediction.voteShare() == 1, "exact k-NN match");
        }
        expect(IllegalArgumentException.class, () -> new KnnClassifier(examples.subList(0, 4), 3), "incomplete category coverage");
        expect(IllegalArgumentException.class, () -> new FeatureVector(new double[]{Double.NaN}), "invalid vector");
        double[] changed = features.values();
        changed[0] = 99;
        check(features.get(0) != 99, "feature encapsulation");
        double[] common = new double[FeatureExtractor.DIMENSIONS], query = new double[FeatureExtractor.DIMENSIONS];
        java.util.Arrays.fill(common, 1.1); java.util.Arrays.fill(query, 1.04);
        for (int i = 0; i < 4; i++) examples.add(new TrainingExample(Category.BACKGROUND, new FeatureVector(common), "imbalanced mechanics fixture"));
        check(new KnnClassifier(examples, 3).classify(new FeatureVector(query)).category() == Category.EXPLOSION,
                "inverse-frequency votes avoid majority-class bias among equally close neighbors");
    }

    private static void trainingAndPersistence() throws Exception {
        Path root = Files.createTempDirectory("omniear-data-test-");
        try {
            var repository = new TrainingRepository(root.resolve("training"));
            check(repository.load().classifier().isEmpty(), "missing training data is untrained, not background");
            Path wave = root.resolve("fixture.wav");
            writeWav(wave, new AudioFormat(16000, 16, 1, true, false), .3, 440, .2);
            Path imported = repository.importWav(wave, Category.BACKGROUND);
            check(Files.isRegularFile(imported), "validated training import");
            check(repository.importWav(wave, Category.BACKGROUND).equals(imported), "content deduplication");
            expect(IOException.class, () -> repository.importWav(wave, Category.EXPLOSION), "conflicting labels");
            check(repository.load().counts().get(Category.BACKGROUND) == 1, "real file feature extraction");
            Files.copy(imported, imported.resolveSibling("renamed-copy.wav"));
            check(repository.load().counts().get(Category.BACKGROUND) == 1, "manually copied WAV does not bias votes");
            Path conflict = root.resolve("training/explosion/conflicting.wav");
            Files.createDirectories(conflict.getParent()); Files.copy(imported, conflict);
            expect(IOException.class, repository::load, "manually labeled conflicting duplicates reject model");
            Files.delete(conflict);
            Files.delete(imported);
            check(repository.importWav(wave, Category.BACKGROUND).getFileName().toString().equals("renamed-copy.wav"), "import recognizes manually named duplicate");
            expect(IOException.class, () -> repository.importWav(wave, Category.EXPLOSION), "import rejects conflicting manual label");
            var reading = new AdaptiveDetector.Reading(.2, .01, .06, true, true);
            var policy = new AlertPolicy();
            var background = policy.event(Instant.now(), "mic", 2, reading, new SoundClassifier.Prediction(Category.BACKGROUND, 1, 0));
            check(!background.isAlert(), "background suppression");
            for (Category category : Category.values()) {
                if (category == Category.BACKGROUND) continue;
                double gate = category == Category.EXPLOSION ? .99 : Math.max(.7, category.alertGate());
                var event = policy.event(Instant.now(), "mic", 2, reading, new SoundClassifier.Prediction(category, gate, 0));
                check(event.isAlert() && event.priority() == category.priority(), "priority and inclusive evidence vote gate");
                check(!policy.event(Instant.now(), "mic", 2, reading,
                        new SoundClassifier.Prediction(category, category.alertGate() - .001, 0)).isAlert(), "low vote suppression");
                check(!policy.event(Instant.now(), "clip", 0, reading,
                        new SoundClassifier.Prediction(category, 1, 2)).isAlert(), "distance rejection also protects direct clip alerts");
            }
            var event = policy.event(Instant.parse("2026-01-01T00:00:00Z"), "a,\"quoted\"\nname.wav", 2, reading,
                    new SoundClassifier.Prediction(Category.SCREAM_DISTRESS, .9, .2));
            Path csv = root.resolve("events.csv");
            try (var store = new EventStore(csv)) {
                store.append(event);
                store.append(policy.event(Instant.now(), "untrained", 3, reading, null));
                expect(IOException.class, () -> { try (var duplicate = new EventStore(csv)) { duplicate.history(); } }, "CSV exclusive lock");
            }
            try (var store = new EventStore(csv)) {
                check(store.history().size() == 2 && store.history().getFirst().equals(event), "CSV UTF-8 quote/newline round trip");
                check(store.history().get(1).status() == SoundEvent.Status.UNTRAINED, "untrained event persistence");
            }
            Path corrupt = root.resolve("corrupt.csv");
            Files.writeString(corrupt, "invalid header\n");
            expect(IOException.class, () -> { try (var store = new EventStore(corrupt)) { store.history(); } }, "corrupt CSV rejected");
            check(Files.readString(corrupt).equals("invalid header\n"), "corrupt CSV not overwritten");
        } finally { deleteTree(root); }
    }

    private static void pipelineAndThreading() throws Exception {
        var config = AdaptiveDetector.Config.defaults();
        var pipeline = new DetectionPipeline(config, null, Instant.parse("2026-01-01T00:00:00Z"), "fixture");
        for (int i = 0; i < 20; i++) check(pipeline.accept(new float[1600]).isEmpty(), "pipeline warmup");
        check(pipeline.accept(constant(1600, .5f)).isEmpty(), "post-trigger capture not immediate");
        for (int i = 0; i < 12; i++) check(pipeline.accept(new float[1600]).isEmpty(), "post-trigger wait");
        var event = pipeline.accept(new float[1600]).orElseThrow();
        check(event.status() == SoundEvent.Status.UNTRAINED && event.category() == null, "no invented prediction");
        check(event.audioSeconds() == 2 && event.timestamp().equals(Instant.parse("2026-01-01T00:00:02Z")), "trigger timestamp, not inference time");
        check(pipeline.finish().isEmpty(), "no duplicate EOF event");
        pipeline = new DetectionPipeline(config, null, Instant.now(), "EOF fixture");
        for (int i = 0; i < 20; i++) pipeline.accept(new float[1600]);
        pipeline.accept(constant(1600, .5f));
        check(pipeline.finish().isPresent(), "partial post-trigger capture flushed at EOF");
        Path root = Files.createTempDirectory("omniear-worker-test-");
        try {
            var done = new CountDownLatch(1);
            var errors = new java.util.concurrent.CopyOnWriteArrayList<Exception>();
            var listener = new MonitorService.Listener() {
                @Override public void onBlock(float[] samples, AdaptiveDetector.Reading reading, double time) { }
                @Override public void onEvent(SoundEvent e) { }
                @Override public void onState(boolean running, String message) { if (!running) done.countDown(); }
                @Override public void onError(String message, Exception error) { errors.add(error); }
            };
            try (var store = new EventStore(root.resolve("events.csv")); var service = new MonitorService(listener, store)) {
                service.start(new WavSource(root.resolve("missing.wav")), config, null);
                check(done.await(5, TimeUnit.SECONDS), "worker reports invalid audio and stops");
                check(!service.isRunning() && errors.size() == 1, "thread recovery");
            }
        } finally { deleteTree(root); }
    }

    private static void integration() throws Exception {
        Path root = Files.createTempDirectory("omniear-integration-test-");
        try {
            Path training = root.resolve("training");
            createMechanicalTrainingFixtures(training);
            var model = new TrainingRepository(training).load().classifier().orElseThrow();
            check(model.size() == 5, "Java WAV training-to-kNN integration (mechanics only)");
            Path wave = root.resolve("event.wav");
            writeEventWav(wave);
            var done = new CountDownLatch(1);
            var emitted = new java.util.concurrent.CopyOnWriteArrayList<SoundEvent>();
            var errors = new java.util.concurrent.CopyOnWriteArrayList<Exception>();
            var listener = new MonitorService.Listener() {
                @Override public void onBlock(float[] samples, AdaptiveDetector.Reading reading, double seconds) { }
                @Override public void onEvent(SoundEvent event) { emitted.add(event); }
                @Override public void onState(boolean running, String message) { if (!running) done.countDown(); }
                @Override public void onError(String message, Exception error) { errors.add(error); }
            };
            Path csv = root.resolve("events.csv");
            try (var store = new EventStore(csv); var service = new MonitorService(listener, store)) {
                service.start(new WavSource(wave), AdaptiveDetector.Config.defaults(), model);
                check(done.await(10, TimeUnit.SECONDS), "complete streaming WAV analysis");
                check(errors.isEmpty(), "integrated WAV worker has no errors");
                check(emitted.size() == 3 && emitted.stream().allMatch(e -> e.category() != null), "three non-silent half-second streaming decisions use real features");
                check(store.history().size() == 3, "all integrated streaming decisions persisted");
            }
            try (var store = new EventStore(csv)) {
                check(store.history().equals(emitted), "integration survives restart");
            }
            var direct = DetectionPipeline.classifyClip(WavSource.load(wave), model, Instant.now(), "fixture");
            check(direct.source().contains("Stage 1 bypass") && direct.threshold() == 0, "explicit short-clip classification route");
            cli(root, training, wave);
            SwingUtilities.invokeAndWait(() -> {
                var table = new EventTableModel(java.util.List.of());
                table.add(direct);
                check(table.getRowCount() == 1 && table.getValueAt(0, 3) != null, "Swing event model updates on EDT");
                check(table.statistics().contains("P0") && table.statistics().contains("Unclassified"), "real event statistics");
                var panel = new WaveformPanel(); panel.setSize(600, 160); panel.setSamples(constant(1600, .2f));
                var image = new java.awt.image.BufferedImage(600, 160, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics(); try { panel.paint(graphics); } finally { graphics.dispose(); }
                check(image.getRGB(50, 80) != 0, "waveform can paint headlessly");
            });
            var blocking = new AudioSource() {
                private volatile boolean closed;
                @Override public void open() { }
                @Override public int read(float[] destination) throws IOException {
                    while (!closed) { try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return 0; } }
                    return 0;
                }
                @Override public String description() { return "test-only blocking input"; }
                @Override public void close() { closed = true; }
            };
            var stopped = new CountDownLatch(1);
            var stopListener = new MonitorService.Listener() {
                @Override public void onBlock(float[] samples, AdaptiveDetector.Reading reading, double seconds) { }
                @Override public void onEvent(SoundEvent event) { throw new AssertionError("Stop must not emit an event"); }
                @Override public void onState(boolean running, String message) { if (!running) stopped.countDown(); }
                @Override public void onError(String message, Exception error) { throw new AssertionError(error); }
            };
            try (var service = new MonitorService(stopListener, null)) {
                service.start(blocking, AdaptiveDetector.Config.defaults(), null); service.stop();
                check(stopped.await(5, TimeUnit.SECONDS) && !service.isRunning(), "Stop releases blocking input, including start/stop race");
            }
        } finally { deleteTree(root); }
    }

    public static void createMechanicalTrainingFixtures(Path root) throws IOException {
        // These tones carry arbitrary labels solely to exercise file/algorithm plumbing.
        // They are NOT acoustic training data and never leave the temporary test directory.
        for (Category category : Category.values()) {
            Path directory = root.resolve(category.id()); Files.createDirectories(directory);
            writeWav(directory.resolve("mechanics-only.wav"), new AudioFormat(16000, 16, 1, true, false),
                    2.5, 300 + 700 * category.ordinal(), .3);
        }
    }

    private static void heldOutEvaluation() throws Exception {
        Path root = Files.createTempDirectory("omniear-holdout-");
        try {
            Path training = root.resolve("training"), testing = root.resolve("testing");
            createMechanicalTrainingFixtures(training);
            Files.createDirectories(testing.resolve(Category.BACKGROUND.id()));
            Path unseen = testing.resolve(Category.BACKGROUND.id()).resolve("unseen-mechanics.wav");
            writeWav(unseen, new AudioFormat(44100, 16, 1, true, false), .4, 730, .17);
            Path copied = testing.resolve(Category.BACKGROUND.id()).resolve("renamed-training.wav");
            Files.copy(training.resolve(Category.BACKGROUND.id()).resolve("mechanics-only.wav"), copied);
            String result = HeldOutEvaluation.evaluate(training, testing);
            check(result.contains("Evaluated: 1") && result.contains("skipped: 1"), "only unseen WAV evaluated");
            check(result.contains("Training overlap") && result.contains("Confusion matrix"), "leakage diagnostics and matrix");
            check(result.contains("precision=") && result.contains("recall=") && result.contains("false positives="), "per-class holdout metrics");
            check(result.contains("Stage 1 → Stage 2") && result.contains("NO_EVENT") && result.contains("background recordings with alerts: 0/1"), "raw pipeline includes missed short recordings and false alerts");
            expect(IOException.class, () -> HeldOutEvaluation.evaluate(training, training), "reject same train/test root");
            Files.delete(unseen);
            check(HeldOutEvaluation.evaluate(training, testing).contains("No accuracy calculated"), "no score without eligible test samples");
        } finally { deleteTree(root); }
    }

    private static void cli(Path root, Path training, Path input) throws Exception {
        String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        var train = new ProcessBuilder(javaCommand, "-Djava.awt.headless=true", "-cp", classpath, "omniear.Main", "--train", training.toString())
                .redirectErrorStream(true).start();
        String output = new String(train.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        check(train.waitFor() == 0 && output.contains("Java k-NN ready: 5 WAVs"), "headless training CLI");
        Path data = root.resolve("cli-data");
        var classify = new ProcessBuilder(javaCommand, "-Djava.awt.headless=true", "-cp", classpath, "omniear.Main", "--classify",
                training.resolve(Category.SCREAM_DISTRESS.id()).resolve("mechanics-only.wav").toString(), "--training", training.toString(), "--data", data.toString())
                .redirectErrorStream(true).start();
        output = new String(classify.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        check(classify.waitFor() == 0 && output.contains("P0"), "headless clip CLI with actual exact-match kNN prediction");
        try (var store = new EventStore(data.resolve("events.csv"))) { check(store.history().size() == 1 && store.history().getFirst().isAlert(), "CLI event durable"); }
        var missing = new ProcessBuilder(javaCommand, "-Djava.awt.headless=true", "-cp", classpath, "omniear.Main", "--classify", input.toString(),
                "--training", root.resolve("missing-training").toString()).redirectErrorStream(true).start();
        output = new String(missing.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        check(missing.waitFor() == 2 && output.contains("Cannot classify without real training"), "CLI refuses invented predictions");
        var analyze = new ProcessBuilder(javaCommand, "-Djava.awt.headless=true", "-cp", classpath, "omniear.Main", "--analyze", input.toString(),
                "--training", root.resolve("missing-training").toString(), "--data", data.toString()).redirectErrorStream(true).start();
        output = new String(analyze.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        check(analyze.waitFor() == 0 && output.contains("UNTRAINED"), "CLI gated analysis works without a model");
    }

    public static void writeEventWav(Path path) throws IOException {
        var format = new AudioFormat(16000, 16, 1, true, false);
        int frames = 16000 * 4;
        byte[] bytes = new byte[frames * 2];
        for (int i = 40000; i < 52000; i++) {
            int raw = (int) Math.round(.5 * 32767 * Math.sin(2 * Math.PI * 1000 * i / 16000));
            bytes[2 * i] = (byte) raw; bytes[2 * i + 1] = (byte) (raw >> 8);
        }
        try (var stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile());
        }
    }

    public static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    public static float[] constant(int n, float value) {
        float[] result = new float[n];
        java.util.Arrays.fill(result, value);
        return result;
    }

    public static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    public static void expect(Class<? extends Throwable> type, CheckedAction action, String name) {
        try { action.run(); } catch (Throwable e) {
            check(type.isInstance(e), name + ": " + e);
            return;
        }
        throw new AssertionError(name + ": expected " + type.getSimpleName());
    }

    @FunctionalInterface
    public interface CheckedAction { void run() throws Exception; }
}
