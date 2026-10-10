package omniear;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import omniear.audio.*;
import omniear.alerts.EventStore;
import omniear.detection.*;
import omniear.model.*;
import omniear.ui.Dashboard;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        try { if (!launch(args)) System.exit(2); }
        catch (Exception e) { System.err.println("OmniEar: " + e.getMessage()); System.exit(2); }
    }
    private static boolean launch(String[] args) throws Exception {
        Path data = Path.of("data"), training = null, input = null;
        String mode = "gui";
        for (int i = 0; i < args.length; i++) {
            String flag = args[i];
            if (flag.equals("--help")) { usage(); return true; }
            if (!java.util.Set.of("--data", "--training", "--train", "--analyze", "--classify").contains(flag) || i + 1 == args.length) {
                usage(); throw new IllegalArgumentException("Unknown/incomplete option: " + flag);
            }
            Path value = Path.of(args[++i]);
            switch (flag) {
                case "--data" -> data = value;
                case "--training" -> training = value;
                case "--train" -> { if (!mode.equals("gui")) throw new IllegalArgumentException("Select only one action"); mode = "train"; training = value; }
                default -> { if (!mode.equals("gui")) throw new IllegalArgumentException("Select only one action"); mode = flag.substring(2); input = value; }
            }
        }
        if (training == null) training = data.resolve("training");
        if (mode.equals("gui")) {
            if (GraphicsEnvironment.isHeadless()) throw new IOException("A graphical desktop is required. Headless mode supports --train, --analyze and --classify.");
            EventStore store = null;
            String problem = null;
            try { store = new EventStore(data.resolve("events.csv")); } catch (IOException e) { problem = "CSV disabled: " + e.getMessage(); }
            EventStore opened = store; String warning = problem; Path trainingRoot = training;
            SwingUtilities.invokeLater(() -> {
                try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
                catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) { System.err.println("Using default Swing appearance: " + e.getMessage()); }
                new Dashboard(trainingRoot, opened, warning).setVisible(true);
            });
            return true;
        }
        var report = new TrainingRepository(training).load();
        System.out.println(report.summary());
        for (Category category : Category.values()) System.out.println("  " + category.id() + ": " + report.counts().getOrDefault(category, 0));
        report.problems().forEach(System.err::println);
        if (mode.equals("train")) return report.classifier().isPresent();
        SoundClassifier model = report.classifier().orElse(null);
        if (mode.equals("classify")) {
            if (model == null) { System.err.println("Cannot classify without real training data for all five categories."); return false; }
            var event = DetectionPipeline.classifyClip(WavSource.load(input), model, Instant.now(), input.getFileName().toString());
            try (var store = new EventStore(data.resolve("events.csv"))) { store.append(event); }
            printEvent(event); return true;
        }
        var done = new CountDownLatch(1);
        var failed = new AtomicBoolean();
        var listener = new MonitorService.Listener() {
            @Override public void onBlock(float[] samples, AdaptiveDetector.Reading reading, double seconds) { }
            @Override public void onEvent(SoundEvent event) { printEvent(event); }
            @Override public void onState(boolean running, String message) { System.out.println(message); if (!running) done.countDown(); }
            @Override public void onError(String message, Exception error) { failed.set(true); System.err.println(message + ": " + error.getMessage()); }
        };
        try (var store = new EventStore(data.resolve("events.csv")); var monitor = new MonitorService(listener, store)) {
            monitor.start(new WavSource(input), AdaptiveDetector.Config.defaults(), model); done.await();
        }
        return !failed.get();
    }
    private static void printEvent(SoundEvent event) {
        System.out.printf(java.util.Locale.ROOT, "%s · audio %.2fs · %s · %s · votes %.3f · %s%n", event.timestamp(), event.audioSeconds(),
                event.category() == null ? "unclassified" : event.category().id(), event.priority(), event.voteShare(), event.status());
    }
    private static void usage() {
        System.out.println("OmniEar Java 21 (standard JDK only)\n"
                + "  java -cp build/classes omniear.Main [--data DIR] [--training DIR]\n"
                + "  java -cp build/classes omniear.Main --train LABELED_WAV_ROOT\n"
                + "  java -cp build/classes omniear.Main --analyze FILE.wav [--training DIR] [--data DIR]\n"
                + "  java -cp build/classes omniear.Main --classify FILE.wav [--training DIR] [--data DIR]\n"
                + "--analyze preserves Stage 1 calibration; --classify explicitly bypasses Stage 1.");
    }
}
