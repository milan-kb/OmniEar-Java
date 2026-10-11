package omniear.model;

/** Classifier contract used by the pipeline; tests use the real k-NN implementation. */
public interface SoundClassifier {
    Prediction classify(FeatureVector features);

    /** A conflict rejects alert routing; category/vote/distance remain the actual k-NN result. */
    record Prediction(Category category, double voteShare, double nearestDistance, boolean backgroundConflict) {
        public Prediction(Category category, double voteShare, double nearestDistance) {
            this(category, voteShare, nearestDistance, false);
        }
        public Prediction {
            java.util.Objects.requireNonNull(category);
            if (!Double.isFinite(voteShare) || voteShare < 0 || voteShare > 1
                    || !Double.isFinite(nearestDistance) || nearestDistance < 0) throw new IllegalArgumentException("Invalid prediction");
            if (backgroundConflict && category != Category.SCREAM_DISTRESS && category != Category.IMPACT_CRASH)
                throw new IllegalArgumentException("Background conflict is calibrated only for scream and impact");
        }
    }
}
