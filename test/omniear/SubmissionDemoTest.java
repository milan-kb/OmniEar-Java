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
            TestSuite.check(samples.size() == 10 && model.size() == 170, "ten separate demo resources, 170 training recordings");
            Map<Category, Integer> matches = new EnumMap<>(Category.class), misses = new EnumMap<>(Category.class);
            String[] observed = {"scream_distress", "siren_traffic", "explosion", "background", "impact_crash", "explosion", "siren_traffic", "background", "background", "scream_distress"};
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
                System.out.printf(Locale.ROOT, "%s\t%s\t%s\t%.6f\t%.6f\t%s\t%s\t%d%n", sample.fileName(), sample.label().id(),
                        direct.category().id(), direct.voteShare(), direct.nearestDistance(), direct.status(),
                        events.isEmpty() ? "NO_EVENT" : events.getFirst().category().id(), events.stream().filter(SoundEvent::isAlert).count());
            }
            for (Category category : Category.values()) TestSuite.check(matches.getOrDefault(category, 0) == 1 && misses.getOrDefault(category, 0) == 1,
                    "demo includes a direct success and failure for " + category.id());
            System.err.println("PASS: selected examples include five direct matches and five mismatches; this is not a representative accuracy estimate. Raw replay is simulated, not a microphone test.");
        } finally { TestSuite.deleteTree(root); }
    }
}
