import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import omniear.audio.*;
import omniear.detection.*;
import omniear.model.*;

/** Diagnostic-only evidence. Original scaling, votes, detector and predictions are unchanged. */
class RejectionStudy {
    record Example(Category category, FeatureVector features, String group, String path) { }
    static double[] scale = new double[60];
    static double distance(FeatureVector a, FeatureVector b) {
        double sum = 0;
        for (int i = 0; i < scale.length; i++) sum += Math.pow((a.get(i) - b.get(i)) / scale[i], 2);
        return Math.sqrt(sum / scale.length);
    }
    public static void main(String[] args) throws Exception {
        var training = new ArrayList<Example>();
        var negatives = new ArrayList<Example>();
        var extractor = new FeatureExtractor();
        for (String line : Files.readAllLines(Path.of("datasets/groups.tsv"))) {
            String[] r = line.split("\t");
            if (!r[0].startsWith("datasets/training/") && !r[0].startsWith("datasets/additional-training/background/")) continue;
            Category category = Category.fromId(r[1]);
            float[] audio = WavSource.load(Path.of(r[0]));
            var e = new Example(category, extractor.extract(audio), r[2], r[0]);
            if (r[0].startsWith("datasets/training/")) training.add(e);
            if (category == Category.BACKGROUND) {
                negatives.add(e);
                // Real background replay windows, not extra independent source recordings.
                SoundClassifier capture = features -> {
                    negatives.add(new Example(category, features, r[2], r[0] + "#capture"));
                    return new SoundClassifier.Prediction(Category.BACKGROUND, 1, 0);
                };
                var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), capture, Instant.EPOCH, "negative capture");
                for (int i = 0; i < 30; i++) pipeline.accept(new float[1600]);
                for (int i = 0; i < audio.length; i += 1600) pipeline.accept(Arrays.copyOfRange(audio, i, Math.min(audio.length, i + 1600)));
                pipeline.finish();
            }
        }
        double[] mean = new double[60];
        for (var e : training) for (int i = 0; i < 60; i++) mean[i] += e.features.get(i) / training.size();
        for (var e : training) for (int i = 0; i < 60; i++) scale[i] += Math.pow(e.features.get(i) - mean[i], 2) / training.size();
        for (int i = 0; i < 60; i++) scale[i] = scale[i] < 1e-12 ? 1 : Math.sqrt(scale[i]);
        double[] radius = new double[5];
        for (Category category : Category.values()) {
            var distances = new ArrayList<Double>();
            for (var a : training) if (a.category == category) distances.add(training.stream()
                    .filter(b -> b.category == category && !b.group.equals(a.group)).mapToDouble(b -> distance(a.features, b.features)).min().orElseThrow());
            distances.sort(Double::compare);
            radius[category.ordinal()] = distances.get((int) Math.ceil(.95 * distances.size()) - 1);
            System.err.println(category.id() + " leave-source-group-out 95% nearest-positive radius=" + radius[category.ordinal()]);
        }
        var model = new TrainingRepository(Path.of("datasets/training")).load().classifier().orElseThrow();
        ReplayEvaluation.extraHeader = "\twinner_distance\tbackground_ratio\thard_negative_ratio\tgroup_radius_ratio\tnearest_negative";
        ReplayEvaluation.suppliedModel = features -> {
            var p = model.classify(features);
            double positive = training.stream().filter(e -> e.category == p.category()).mapToDouble(e -> distance(features, e.features)).min().orElseThrow();
            double background = training.stream().filter(e -> e.category == Category.BACKGROUND).mapToDouble(e -> distance(features, e.features)).min().orElseThrow();
            var nearest = negatives.stream().min(Comparator.comparingDouble(e -> distance(features, e.features))).orElseThrow();
            ReplayEvaluation.extraFields = String.format(Locale.ROOT, "\t%.8f\t%.8f\t%.8f\t%.8f\t%s", positive,
                    background / Math.max(1e-9, positive), distance(features, nearest.features) / Math.max(1e-9, positive),
                    positive / radius[p.category().ordinal()], nearest.path);
            return p;
        };
        ReplayEvaluation.main(new String[]{"datasets/training", "rejection-dev"});
        System.err.println("Negative reference windows=" + negatives.size() + "; source recordings=82; no development audio in reference index");
    }
}
