package omniear.model;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import omniear.audio.AudioSamples;
import omniear.audio.WavSource;
import omniear.detection.*;

/** Explicit labeled holdout evaluation; never learns from test recordings or emits alerts. */
public final class HeldOutEvaluation {
    private HeldOutEvaluation() { }

    public static String evaluate(Path training, Path testing) throws IOException {
        Path train = training.toRealPath(), test = testing.toRealPath();
        if (test.startsWith(train) || train.startsWith(test)) throw new IOException("Training and test folders must be separate, not nested.");
        var report = new TrainingRepository(train).load();
        var model = report.classifier().orElseThrow(() -> new IOException("Need training WAVs for all five classes before evaluation."));
        var known = new HashSet<String>();
        for (Category category : Category.values()) for (Path path : wavs(train.resolve(category.id()))) known.add(TrainingRepository.fingerprint(path));
        var seen = new HashSet<String>();
        int[][] matrix = new int[Category.values().length][Category.values().length];
        int[][] gated = new int[Category.values().length][Category.values().length + 1];
        int[][] calibrated = new int[Category.values().length][Category.values().length + 1];
        int[] detected = new int[Category.values().length], routed = new int[Category.values().length];
        int backgroundAlerts = 0, directBackgroundAlerts = 0, calibratedBackgroundAlerts = 0, eventCount = 0, alertCount = 0;
        int[] calibratedRouted = new int[Category.values().length];
        double seconds = 0;
        int total = 0, correct = 0, skipped = 0;
        var output = new StringBuilder("Held-out WAV classification · Stage 1 bypassed\nTraining-only scaling; vote shares are not probabilities.\n");
        for (String problem : report.problems()) output.append("Training: ").append(problem).append('\n');
        var extractor = new FeatureExtractor();
        for (Category expected : Category.values()) {
            for (Path path : wavs(test.resolve(expected.id()))) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Evaluation cancelled");
                if (total + skipped >= 5000) throw new IOException("Evaluation limit: 5,000 WAVs");
                try {
                    String digest = TrainingRepository.fingerprint(path);
                    if (known.contains(digest)) throw new IOException("Training overlap: excluded (copied/renamed recording)");
                    if (!seen.add(digest)) throw new IOException("Duplicate test recording: excluded");
                    float[] audio = WavSource.load(path);
                    var prediction = model.classify(extractor.extract(audio));
                    var events = replay(audio, model, 0);
                    var calibratedEvents = replay(audio, model, 30);
                    calibrated[expected.ordinal()][calibratedEvents.isEmpty() ? Category.values().length : calibratedEvents.getFirst().category().ordinal()]++;
                    if (calibratedEvents.stream().anyMatch(e -> e.isAlert() && e.category() == expected)) calibratedRouted[expected.ordinal()]++;
                    if (expected == Category.BACKGROUND) {
                        if (calibratedEvents.stream().anyMatch(SoundEvent::isAlert)) calibratedBackgroundAlerts++;
                        if (prediction.category() != Category.BACKGROUND && prediction.voteShare() >= prediction.category().alertGate()) directBackgroundAlerts++;
                    }
                    int column = events.isEmpty() ? Category.values().length : events.getFirst().category().ordinal();
                    gated[expected.ordinal()][column]++;
                    if (!events.isEmpty()) detected[expected.ordinal()]++;
                    if (events.stream().anyMatch(e -> e.isAlert() && e.category() == expected)) routed[expected.ordinal()]++;
                    if (expected == Category.BACKGROUND && events.stream().anyMatch(SoundEvent::isAlert)) backgroundAlerts++;
                    eventCount += events.size();
                    alertCount += (int) events.stream().filter(SoundEvent::isAlert).count();
                    seconds += audio.length / (double) AudioSamples.SAMPLE_RATE;
                    matrix[expected.ordinal()][prediction.category().ordinal()]++;
                    total++;
                    if (expected == prediction.category()) correct++;
                    output.append(String.format(Locale.ROOT, "%s → %s · vote %.3f · distance %.3f · pipeline %s · calibrated %s%n",
                            test.relativize(path), prediction.category().id(), prediction.voteShare(), prediction.nearestDistance(),
                            eventLabels(events), eventLabels(calibratedEvents)));
                } catch (IOException | IllegalArgumentException e) {
                    skipped++;
                    output.append(test.relativize(path)).append(" · SKIPPED: ").append(e.getMessage()).append('\n');
                }
            }
        }
        appendMetrics(output, matrix, "Direct classification Confusion matrix");
        output.append("Missing test classes: ").append(Arrays.stream(Category.values())
                .filter(c -> Arrays.stream(matrix[c.ordinal()]).sum() == 0).map(Category::id).toList()).append('\n');
        output.append("Evaluated: ").append(total).append(" · correct: ").append(correct).append(" · skipped: ").append(skipped).append('\n');
        if (total > 0) output.append(String.format(Locale.ROOT, "Observed accuracy on these files only: %.2f%%%n", 100.0 * correct / total));
        else output.append("No eligible unseen recordings. No accuracy calculated.\n");
        output.append("\nStage 1 → Stage 2: original WAVs, default detector, no added silence or gain changes.\n")
                .append("Trained periodic one-second windows bypass the RMS warmup barrier; first emitted decision supplies the matrix label. NO_EVENT is an abstention.\n");
        appendMetrics(output, gated, "Raw gated Confusion matrix");
        int gatedCorrect = 0;
        for (Category category : Category.values()) {
            int i = category.ordinal(), support = Arrays.stream(gated[i]).sum();
            gatedCorrect += gated[i][i];
            output.append(category.id()).append(": recordings with streaming decisions=").append(detected[i]).append('/').append(support)
                    .append("; correct-category routed alerts=").append(routed[i]).append('/').append(support).append('\n');
        }
        output.append(String.format(Locale.ROOT, "Gated label accuracy (abstentions count as misses): %s; events=%d; alerts=%d; audio=%.2fs%n",
                percent(gatedCorrect, total), eventCount, alertCount, seconds));
        int backgroundTotal = Arrays.stream(matrix[Category.BACKGROUND.ordinal()]).sum();
        output.append("Direct-clip background recordings passing alert gates: ").append(directBackgroundAlerts).append('/').append(backgroundTotal).append('\n');
        output.append("Pipeline background recordings with alerts: ").append(backgroundAlerts).append('/').append(backgroundTotal)
                .append(" (").append(percent(backgroundAlerts, backgroundTotal)).append(")\n");
        output.append("Clip-level weak labels have no event timestamps. These replay metrics are not live-event detection accuracy or false alarms per deployed hour.\n");
        output.append("\nControlled calibration diagnostic: 3 s DIGITAL SILENCE then each unchanged real WAV.\n")
                .append("Artificial quiet context, NOT additional recordings, training examples, or live detection evidence. Same default thresholds.\n");
        appendMetrics(output, calibrated, "Calibrated replay Confusion matrix");
        int calibratedCorrect = 0;
        for (Category category : Category.values()) {
            int i = category.ordinal(); calibratedCorrect += calibrated[i][i];
            output.append(category.id()).append(": correct-category routed alerts=").append(calibratedRouted[i]).append('/')
                    .append(Arrays.stream(calibrated[i]).sum()).append('\n');
        }
        output.append("Calibrated first-event label accuracy (abstentions count as misses): ").append(percent(calibratedCorrect, total)).append('\n')
                .append("Calibrated background recordings with alerts: ").append(calibratedBackgroundAlerts).append('/').append(backgroundTotal).append('\n');
        output.append("Missing test classes and related/re-encoded excerpts limit interpretation. Split by original recording/source before importing.\n");
        return output.toString();
    }

    private static List<SoundEvent> replay(float[] audio, SoundClassifier model, int quietBlocks) {
        var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), model, Instant.EPOCH, "held-out replay");
        var events = new ArrayList<SoundEvent>();
        for (int i = 0; i < quietBlocks; i++) pipeline.accept(new float[AudioSamples.BLOCK_SIZE]).ifPresent(events::add);
        for (int start = 0; start < audio.length; start += AudioSamples.BLOCK_SIZE) {
            pipeline.accept(Arrays.copyOfRange(audio, start, Math.min(audio.length, start + AudioSamples.BLOCK_SIZE))).ifPresent(events::add);
        }
        pipeline.finish().ifPresent(events::add);
        return events;
    }

    private static List<String> eventLabels(List<SoundEvent> events) {
        return events.stream().map(e -> String.format(Locale.ROOT, "%.2fs:%s:%s", e.audioSeconds(), e.category().id(), e.status())).toList();
    }

    private static void appendMetrics(StringBuilder output, int[][] matrix, String title) {
        output.append('\n').append(title).append(": rows = expected, columns = predicted\n");
        var columns = new ArrayList<>(Arrays.stream(Category.values()).map(Category::id).toList());
        if (matrix[0].length > columns.size()) columns.add("NO_EVENT");
        output.append(columns).append('\n');
        for (Category category : Category.values()) output.append(category.id()).append(": ").append(Arrays.toString(matrix[category.ordinal()])).append('\n');
        for (Category category : Category.values()) {
            int i = category.ordinal(), support = Arrays.stream(matrix[i]).sum(), predicted = 0;
            for (int[] row : matrix) predicted += row[i];
            output.append(category.id()).append(": n=").append(support).append("; precision=").append(percent(matrix[i][i], predicted))
                    .append("; recall=").append(percent(matrix[i][i], support)).append("; false positives=").append(predicted - matrix[i][i]).append('\n');
        }
    }

    private static String percent(int numerator, int denominator) {
        return denominator == 0 ? "N/A" : String.format(Locale.ROOT, "%.2f%%", 100.0 * numerator / denominator);
    }

    private static List<Path> wavs(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".wav")).sorted().toList();
        }
    }
}
