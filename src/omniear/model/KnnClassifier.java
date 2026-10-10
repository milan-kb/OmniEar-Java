package omniear.model;

import java.util.*;

/** Immutable model: training-only z-score scaling and inverse-distance k-neighbor voting. */
public final class KnnClassifier implements SoundClassifier {
    private final List<TrainingExample> examples;
    private final double[] mean = new double[FeatureExtractor.DIMENSIONS];
    private final double[] scale = new double[FeatureExtractor.DIMENSIONS];
    private final int k;
    private record Neighbor(Category category, double distance) { }

    public KnnClassifier(List<TrainingExample> examples, int k) {
        this.examples = List.copyOf(examples);
        if (k < 1) throw new IllegalArgumentException("k must be positive");
        var categories = EnumSet.noneOf(Category.class);
        examples.forEach(e -> categories.add(e.category()));
        if (categories.size() != Category.values().length) throw new IllegalArgumentException("Provide real WAV training examples for all five categories, including background.");
        this.k = Math.min(k, examples.size());
        for (var example : examples) for (int i = 0; i < mean.length; i++) mean[i] += example.features().get(i) / examples.size();
        for (var example : examples) for (int i = 0; i < scale.length; i++) scale[i] += Math.pow(example.features().get(i) - mean[i], 2) / examples.size();
        for (int i = 0; i < scale.length; i++) scale[i] = scale[i] < 1e-12 ? 1 : Math.sqrt(scale[i]);
    }

    public int size() { return examples.size(); }

    @Override public Prediction classify(FeatureVector features) {
        var nearest = new PriorityQueue<Neighbor>(Comparator.comparingDouble(Neighbor::distance).reversed());
        // ponytail: exact O(N * 60) search; use a spatial index only if measured dataset latency requires it.
        for (var example : examples) {
            double squared = 0;
            for (int i = 0; i < scale.length; i++) squared += Math.pow((features.get(i) - example.features().get(i)) / scale[i], 2);
            double distance = Math.sqrt(squared / scale.length);
            if (nearest.size() < k) nearest.add(new Neighbor(example.category(), distance));
            else if (distance < nearest.peek().distance()) { nearest.poll(); nearest.add(new Neighbor(example.category(), distance)); }
        }
        double closest = nearest.stream().mapToDouble(Neighbor::distance).min().orElseThrow();
        double[] votes = new double[Category.values().length];
        for (Neighbor neighbor : nearest) {
            if (closest < 1e-9 && neighbor.distance() >= 1e-9) continue;
            votes[neighbor.category().ordinal()] += closest < 1e-9 ? 1 : 1 / Math.max(1e-9, neighbor.distance());
        }
        int best = Category.BACKGROUND.ordinal();
        double total = 0;
        for (int i = 0; i < votes.length; i++) { total += votes[i]; if (votes[i] > votes[best]) best = i; }
        return new Prediction(Category.values()[best], Math.clamp(votes[best] / total, 0, 1), closest);
    }
}
