package omniear;

import java.time.Instant;
import java.nio.file.*;
import java.util.*;
import omniear.alerts.*;
import omniear.detection.AdaptiveDetector;
import omniear.model.*;

/** Geometry-only fixtures: verify a background conflict never silently relabels an event. */
public final class RejectionTest {
    private RejectionTest() { }
    private static FeatureVector point(double value) {
        double[] values = new double[FeatureExtractor.DIMENSIONS]; Arrays.fill(values, value);
        return new FeatureVector(values);
    }
    public static void main(String[] args) throws Exception {
        var examples = new ArrayList<TrainingExample>();
        for (Category c : Category.values()) examples.add(new TrainingExample(c, point(c == Category.SCREAM_DISTRESS ? 0 : c == Category.BACKGROUND ? .05 : 10 + c.ordinal()), "mechanics-only"));
        // Distant background examples alter class-frequency votes, without being near the query.
        for (int i = 0; i < 39; i++) examples.add(new TrainingExample(Category.BACKGROUND, point(20 + i), "mechanics-only"));
        var model = new KnnClassifier(examples, 7);
        var p = model.classify(point(.04));
        TestSuite.check(p.category() == Category.SCREAM_DISTRESS && p.voteShare() >= Category.SCREAM_DISTRESS.alertGate(), "rare-class vote can beat a four-times-closer background neighbor");
        var event = new AlertPolicy().event(Instant.EPOCH, "mechanics", 0,
                new AdaptiveDetector.Reading(.1, 0, .02, true, true), p);
        TestSuite.check(!event.isAlert() && event.category() == Category.SCREAM_DISTRESS,
                "closer-background evidence suppresses alert without fabricating a background label");
        TestSuite.check(p.backgroundConflict() && event.status() == SoundEvent.Status.BACKGROUND_CONFLICT_SUPPRESSED, "rejection reason is visible and typed");
        TestSuite.check(!model.classify(point(.0263)).backgroundConflict() && model.classify(point(.0264)).backgroundConflict(), "scream relative-distance margin separates adjacent queries");
        var exact = model.classify(point(0));
        TestSuite.check(new AlertPolicy().event(Instant.EPOCH, "exact", 0,
                new AdaptiveDetector.Reading(.1, 0, .02, true, true), exact).isAlert(), "exact threat match remains eligible");
        for (Category target : List.of(Category.IMPACT_CRASH, Category.EXPLOSION, Category.SIREN_TRAFFIC)) {
            var other = new ArrayList<TrainingExample>();
            for (Category c : Category.values()) other.add(new TrainingExample(c, point(c == target ? 0 : c == Category.BACKGROUND ? .05 : 10 + c.ordinal()), "mechanics-only"));
            for (int i = 0; i < 39; i++) other.add(new TrainingExample(Category.BACKGROUND, point(20 + i), "mechanics-only"));
            var classifier = new KnnClassifier(other, 7);
            TestSuite.check(!classifier.classify(point(.025)).backgroundConflict(), "equal-distance evidence never rejects a tie");
            var result = classifier.classify(point(.04));
            TestSuite.check(result.category() == target && result.backgroundConflict() == (target == Category.IMPACT_CRASH), "only development-calibrated categories change alert eligibility");
        }
        Path root = Files.createTempDirectory("omniear-rejection-");
        var legacy = new SoundEvent(UUID.randomUUID(), Instant.EPOCH, "legacy", 0, .1, 0, .02,
                Category.SCREAM_DISTRESS, p.voteShare(), p.nearestDistance(), Priority.P0, SoundEvent.Status.ALERT);
        try {
            try (var store = new EventStore(root.resolve("events.csv"))) { store.append(legacy); store.append(event); }
            try (var store = new EventStore(root.resolve("events.csv"))) {
                TestSuite.check(store.history().equals(List.of(legacy, event)), "old alerts and new rejection statuses coexist after CSV restart");
            }
        } finally { TestSuite.deleteTree(root); }
        System.out.println("PASS: background-conflict geometry regression");
    }
}
