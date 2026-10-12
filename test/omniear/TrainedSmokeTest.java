package omniear;

import java.nio.file.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
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
            TestSuite.check(report.classifier().isPresent() && report.classifier().orElseThrow().size() == 170, "bundled real model has 170 accepted recordings");
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
                        Files.createDirectories(Path.of("build"));
                        var image = new AtomicReference<java.awt.image.BufferedImage>();
                        SwingUtilities.invokeAndWait(() -> {
                            var frame = reference.get(); frame.setSize(frame.getMinimumSize()); frame.validate();
                            var demoButton = findDemoButton(frame);
                            TestSuite.check(demoButton.getVisibleRect().height == demoButton.getHeight() && demoButton.getHeight() > 0,
                                    "primary guided-demo action fully visible at minimum window size");
                            var rendered = new java.awt.image.BufferedImage(frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                            var graphics = rendered.createGraphics();
                            try { frame.printAll(graphics); } finally { graphics.dispose(); }
                            image.set(rendered);
                        });
                        javax.imageio.ImageIO.write(image.get(), "png", Path.of("build/submission-demo.png").toFile());
                        SwingUtilities.invokeAndWait(() -> {
                            var examples = findExamples(reference.get());
                            TestSuite.check(examples != null && examples.getItemCount() == 10, "all licensed demo choices visible");
                            examples.setSelectedIndex(9); // Real wind recording: known false scream alert.
                            findDemoButton(reference.get()).doClick();
                        });
                        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                        boolean done = false;
                        while (!done && System.nanoTime() < deadline) {
                            var ready = new java.util.concurrent.atomic.AtomicBoolean();
                            SwingUtilities.invokeAndWait(() -> ready.set(!reference.get().isBusy() && reference.get().eventCount() == 2));
                            done = ready.get(); if (!done) Thread.sleep(25);
                        }
                        TestSuite.check(done, "guided demo button completes inference");
                        SwingUtilities.invokeAndWait(() -> {
                            TestSuite.check(reference.get().predictionText().contains("LABEL MISMATCH")
                                    && reference.get().predictionText().contains("not live microphone detection"), "actual demo failure and mode distinction visible");
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
                    TestSuite.check(process.waitFor() == 0 && output.contains("Java k-NN ready: 170 WAVs") && output.contains("votes"), "fresh startup/restart classify real unseen audio");
                }
                try (var store = new EventStore(data.resolve("events.csv"))) {
                    var events = store.history();
                    TestSuite.check(events.size() == 2 && events.get(0).category() == events.get(1).category()
                            && events.get(0).nearestDistance() == events.get(1).nearestDistance(), "real prediction persists and is deterministic across restarts");
                }
            }
            System.out.println("PASS: trained fresh-start smoke test (170 accepted real training WAVs; real development inference)");
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
    private static JComboBox<?> findExamples(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JComboBox<?> box && box.getItemCount() == 10) return box;
            if (child instanceof Container container) { var found = findExamples(container); if (found != null) return found; }
        }
        return null;
    }
    private static JButton findDemoButton(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && button.getText().equals("Classify example")) return button;
            if (child instanceof Container container) { var found = findDemoButton(container); if (found != null) return found; }
        }
        return null;
    }
}
