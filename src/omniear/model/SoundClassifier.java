package omniear.model;

/** Classifier contract used by the pipeline; tests use the real k-NN implementation. */
public interface SoundClassifier {
    Prediction classify(FeatureVector features);

    record Prediction(Category category, double voteShare, double nearestDistance) {
        public Prediction {
            java.util.Objects.requireNonNull(category);
            if (!Double.isFinite(voteShare) || voteShare < 0 || voteShare > 1
                    || !Double.isFinite(nearestDistance) || nearestDistance < 0) throw new IllegalArgumentException("Invalid prediction");
        }
    }
}
