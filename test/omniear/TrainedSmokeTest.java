package omniear;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import omniear.alerts.EventStore;
import omniear.model.TrainingRepository;
import omniear.ui.*;

/** Real bundled training and real held-out audio. Checks readiness, not an accuracy target. */
public final class TrainedSmokeTest {
    private TrainedSmokeTest() { }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("omniear-trained-");
        Path input = Path.of("datasets/held-out/scream_distress/fsd-264918.wav").toAbsolutePath();
        try {
            Path training = root.resolve("training");
            TrainingRepository.installBundled(training);
            var report = new TrainingRepository(training).load();
            TestSuite.check(report.classifier().isPresent() && report.classifier().orElseThrow().size() == 217, "bundled real model has 217 recordings");
            TestSuite.check(report.problems().isEmpty(), "all bundled audio decodes and trains");
            Path marker = training.resolve("user-note.txt"); Files.writeString(marker, "preserve me");
            TrainingRepository.installBundled(training);
            TestSuite.check(Files.readString(marker).equals("preserve me"), "startup preserves existing training root");
            if (args.length > 0 && args[0].equals("--ui")) {
                var reference = new AtomicReference<Dashboard>();
                try (var store = new EventStore(root.resolve("ui-events.csv"))) {
                    try {
                        SwingUtilities.invokeAndWait(() -> { DarkTheme.install(); var ui = new Dashboard(training, store, null); reference.set(ui); ui.setVisible(true); });
                        waitFor(reference, false);
                        SwingUtilities.invokeAndWait(() -> reference.get().classifyWav(input));
                        waitFor(reference, true);
                        SwingUtilities.invokeAndWait(() -> {
                            TestSuite.check(reference.get().predictionText().contains("Nearest standardized distance"), "real held-out prediction visible");
                            System.out.println(reference.get().predictionText());
                        });
                    } finally {
                        if (reference.get() != null) SwingUtilities.invokeAndWait(() -> reference.get().closeApplication());
                    }
                }
            } else {
                // First CLI launch installs automatically; second reuses the persisted recordings.
                Path data = root.resolve("fresh-default");
                for (int i = 0; i < 2; i++) {
                    var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "-cp", System.getProperty("java.class.path"), "omniear.Main", "--classify", input.toString(), "--data", data.toString())
                            .redirectErrorStream(true).start();
                    String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    TestSuite.check(process.waitFor() == 0 && output.contains("Java k-NN ready: 217 WAVs") && output.contains("votes"), "fresh startup/restart classify real unseen audio");
                }
                try (var store = new EventStore(data.resolve("events.csv"))) {
                    var events = store.history();
                    TestSuite.check(events.size() == 2 && events.get(0).category() == events.get(1).category()
                            && events.get(0).nearestDistance() == events.get(1).nearestDistance(), "real prediction persists and is deterministic across restarts");
                }
            }
            System.out.println("PASS: trained fresh-start smoke test (217 real training WAVs; real development inference)");
        } finally { TestSuite.deleteTree(root); }
    }

    private static void waitFor(AtomicReference<Dashboard> reference, boolean classified) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> ready.set(!reference.get().isBusy() && reference.get().hasClassifier()
                    && (!classified || reference.get().eventCount() == 1)));
            if (ready.get()) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Trained dashboard did not become ready");
    }
}
