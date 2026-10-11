package omniear.model;

import java.util.*;

/** Immutable model: training-only scaling and inverse-distance, class-balanced voting. */
public final class KnnClassifier implements SoundClassifier {
    public static final int DEFAULT_K = 7;
    private final List<TrainingExample> examples;
    private final double[] mean = new double[FeatureExtractor.DIMENSIONS];
    private final double[] scale = new double[FeatureExtractor.DIMENSIONS];
    private final int k;
    private final int[] classCounts = new int[Category.values().length];
    private record Neighbor(Category category, double distance) { }

    public KnnClassifier(List<TrainingExample> examples, int k) {
        this.examples = List.copyOf(examples);
        if (k < 1) throw new IllegalArgumentException("k must be positive");
        var categories = EnumSet.noneOf(Category.class);
        examples.forEach(e -> categories.add(e.category()));
        if (categories.size() != Category.values().length) throw new IllegalArgumentException("Provide real WAV training examples for all five categories, including background.");
        this.k = Math.min(k, examples.size());
        for (var example : examples) classCounts[example.category().ordinal()]++;
        for (var example : examples) for (int i = 0; i < mean.length; i++) mean[i] += example.features().get(i) / examples.size();
        for (var example : examples) for (int i = 0; i < scale.length; i++) scale[i] += Math.pow(example.features().get(i) - mean[i], 2) / examples.size();
        for (int i = 0; i < scale.length; i++) scale[i] = scale[i] < 1e-12 ? 1 : Math.sqrt(scale[i]);
    }

    public int size() { return examples.size(); }

    @Override public Prediction classify(FeatureVector features) {
        var nearest = new PriorityQueue<Neighbor>(Comparator.comparingDouble(Neighbor::distance).reversed());
        double[] closestByClass = new double[Category.values().length];
        Arrays.fill(closestByClass, Double.POSITIVE_INFINITY);
        // ponytail: exact O(N * 60) search; use a spatial index only if measured dataset latency requires it.
        for (var example : examples) {
            double squared = 0;
            for (int i = 0; i < scale.length; i++) squared += Math.pow((features.get(i) - example.features().get(i)) / scale[i], 2);
            double distance = Math.sqrt(squared / scale.length);
            closestByClass[example.category().ordinal()] = Math.min(closestByClass[example.category().ordinal()], distance);
            if (nearest.size() < k) nearest.add(new Neighbor(example.category(), distance));
            else if (distance < nearest.peek().distance()) { nearest.poll(); nearest.add(new Neighbor(example.category(), distance)); }
        }
        double closest = nearest.stream().mapToDouble(Neighbor::distance).min().orElseThrow();
        double[] votes = new double[Category.values().length];
        for (Neighbor neighbor : nearest) {
            if (closest < 1e-9 && neighbor.distance() >= 1e-9) continue;
            votes[neighbor.category().ordinal()] += (closest < 1e-9 ? 1 : 1 / Math.max(1e-9, neighbor.distance()))
                    / classCounts[neighbor.category().ordinal()];
        }
        int best = Category.BACKGROUND.ordinal();
        double total = 0;
        for (int i = 0; i < votes.length; i++) { total += votes[i]; if (votes[i] > votes[best]) best = i; }
        Category category = Category.values()[best];
        // Development-selected relative evidence; no new absolute-distance or vote calibration.
        double margin = category == Category.SCREAM_DISTRESS ? .9 : category == Category.IMPACT_CRASH ? 1 : 0;
        boolean conflict = closestByClass[Category.BACKGROUND.ordinal()] < margin * closestByClass[best];
        return new Prediction(category, Math.clamp(votes[best] / total, 0, 1), closest, conflict);
    }
}
