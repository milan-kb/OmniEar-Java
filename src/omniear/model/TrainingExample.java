package omniear.model;

public record TrainingExample(Category category, FeatureVector features, String source) {
    public TrainingExample {
        java.util.Objects.requireNonNull(category);
        java.util.Objects.requireNonNull(features);
        java.util.Objects.requireNonNull(source);
    }
}
