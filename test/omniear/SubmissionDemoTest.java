package omniear;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import omniear.detection.*;
import omniear.model.*;

/** Selected, inspected examples: executable demo/regression, never a new accuracy estimate. */
public final class SubmissionDemoTest {
    private SubmissionDemoTest() { }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("omniear-demo-test-");
        try {
            TrainingRepository.installBundled(root.resolve("training"));
            var model = new TrainingRepository(root.resolve("training")).load().classifier().orElseThrow();
            var samples = DemoSample.all();
            TestSuite.check(samples.size() == 10 && model.size() == 217, "ten separate demo resources, 217 training recordings");
            Map<Category, Integer> matches = new EnumMap<>(Category.class), misses = new EnumMap<>(Category.class);
            String[] observed = {"scream_distress", "siren_traffic", "explosion", "explosion", "impact_crash", "explosion", "siren_traffic", "siren_traffic", "background", "scream_distress"};
            System.out.println("file\tlabel\tdirect_prediction\tvote\tdistance\tstatus\traw_first_prediction\traw_alerts");
            for (int i = 0; i < samples.size(); i++) {
                var sample = samples.get(i); float[] audio = sample.load();
                var direct = DetectionPipeline.classifyClip(audio, model, Instant.EPOCH, sample.fileName());
                TestSuite.check(direct.category().id().equals(observed[i]), "verified demo outcome (including known failure): " + sample.fileName());
                TestSuite.check(direct.nearestDistance() > 1e-9, "demo is not an exact training match");
                (direct.category() == sample.label() ? matches : misses).merge(sample.label(), 1, Integer::sum);
                var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), model, Instant.EPOCH, sample.fileName());
                var events = new ArrayList<SoundEvent>();
                for (int offset = 0; offset < audio.length; offset += 1600)
                    pipeline.accept(Arrays.copyOfRange(audio, offset, Math.min(audio.length, offset + 1600))).ifPresent(events::add);
                pipeline.finish().ifPresent(events::add);
                if (sample.fileName().equals("fsd-238228.wav")) TestSuite.check(events.stream().anyMatch(e -> e.isAlert() && e.category() == sample.label()),
                        "real sustained siren regression routes an alert without a sixfold RMS onset");
                if (sample.fileName().equals("fsd-381853.wav")) TestSuite.check(!direct.isAlert(),
                        "real wind-to-scream confusion no longer routes a direct false alert");
                System.out.printf(Locale.ROOT, "%s\t%s\t%s\t%.6f\t%.6f\t%s\t%s\t%d%n", sample.fileName(), sample.label().id(),
                        direct.category().id(), direct.voteShare(), direct.nearestDistance(), direct.status(),
                        events.isEmpty() ? "NO_EVENT" : events.getFirst().category().id(), events.stream().filter(SoundEvent::isAlert).count());
            }
            for (Category category : Category.values()) TestSuite.check(matches.getOrDefault(category, 0) + misses.getOrDefault(category, 0) == 2,
                    "both historical demo recordings retained for " + category.id());
            TestSuite.check(matches.values().stream().mapToInt(Integer::intValue).sum() == 7, "seven direct demo regressions match, three known confusions remain");
            TestSuite.check(!DetectionPipeline.classifyClip(new float[40000], model, Instant.EPOCH, "digital silence").isAlert(), "bundled model cannot alert on direct digital silence");
            System.err.println("PASS: seven matches on historical selected examples; not an accuracy estimate. Raw replay is simulated, not a microphone test.");
        } finally { TestSuite.deleteTree(root); }
    }
}
