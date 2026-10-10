package omniear.model;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import omniear.audio.WavSource;

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
                    var prediction = model.classify(extractor.extract(WavSource.load(path)));
                    matrix[expected.ordinal()][prediction.category().ordinal()]++;
                    total++;
                    if (expected == prediction.category()) correct++;
                    output.append(String.format(Locale.ROOT, "%s → %s · vote %.3f · distance %.3f%n",
                            test.relativize(path), prediction.category().id(), prediction.voteShare(), prediction.nearestDistance()));
                } catch (IOException | IllegalArgumentException e) {
                    skipped++;
                    output.append(test.relativize(path)).append(" · SKIPPED: ").append(e.getMessage()).append('\n');
                }
            }
        }
        output.append("\nConfusion matrix: rows = expected, columns = predicted\n");
        output.append(Arrays.toString(Arrays.stream(Category.values()).map(Category::id).toArray())).append('\n');
        for (Category category : Category.values()) output.append(category.id()).append(": ").append(Arrays.toString(matrix[category.ordinal()])).append('\n');
        output.append("Missing test classes: ").append(Arrays.stream(Category.values())
                .filter(c -> Arrays.stream(matrix[c.ordinal()]).sum() == 0).map(Category::id).toList()).append('\n');
        output.append("Evaluated: ").append(total).append(" · correct: ").append(correct).append(" · skipped: ").append(skipped).append('\n');
        if (total > 0) output.append(String.format(Locale.ROOT, "Observed accuracy on these files only: %.2f%%%n", 100.0 * correct / total));
        else output.append("No eligible unseen recordings. No accuracy calculated.\n");
        output.append("Missing test classes and related/re-encoded excerpts limit interpretation. Split by original recording/source before importing.\n");
        return output.toString();
    }

    private static List<Path> wavs(Path root) throws IOException {
        if (!Files.isDirectory(root)) return List.of();
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".wav")).sorted().toList();
        }
    }
}
