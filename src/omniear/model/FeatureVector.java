package omniear.model;

/** Immutable, defensively copied numeric value object. */
public final class FeatureVector {
    private final double[] values;
    public FeatureVector(double[] values) {
        if (values.length != FeatureExtractor.DIMENSIONS) throw new IllegalArgumentException("Expected " + FeatureExtractor.DIMENSIONS + " features");
        this.values = values.clone();
        for (double value : this.values) if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite feature");
    }
    public int size() { return values.length; }
    public double get(int index) { return values[index]; }
    public double[] values() { return values.clone(); }
}
