package omniear.model;

/** Classifier contract used by the pipeline; tests use the real k-NN implementation. */
public interface SoundClassifier {
    Prediction classify(FeatureVector features);

    record Prediction(Category category, double voteShare, double nearestDistance,
            double categoryDistance, double backgroundDistance) {
        public Prediction(Category category, double voteShare, double nearestDistance) {
            this(category, voteShare, nearestDistance, nearestDistance,
                    nearestDistance);
        }
        public Prediction {
            java.util.Objects.requireNonNull(category);
            if (!Double.isFinite(voteShare) || voteShare < 0 || voteShare > 1
                    || !Double.isFinite(nearestDistance) || nearestDistance < 0
                    || !Double.isFinite(categoryDistance) || categoryDistance < 0
                    || !Double.isFinite(backgroundDistance) || backgroundDistance < 0) throw new IllegalArgumentException("Invalid prediction");
        }
        /** Geometric evidence, NOT a probability; lower means farther from known backgrounds. */
        public double backgroundRatio() { return categoryDistance / Math.max(1e-9, backgroundDistance); }
    }
}
