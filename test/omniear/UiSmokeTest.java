package omniear;

import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import omniear.alerts.EventStore;
import omniear.ui.Dashboard;
import omniear.ui.DarkTheme;
import static omniear.SwingTestSupport.find;

/** Optional real-display smoke test. Does not access the microphone or evaluate accuracy. */
public final class UiSmokeTest {
    private UiSmokeTest() { }
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("ui-test requires a graphical desktop");
        Path root = Files.createTempDirectory("omniear-ui-test-");
        var reference = new AtomicReference<Dashboard>();
        var errors = new CopyOnWriteArrayList<Throwable>();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { errors.add(error); error.printStackTrace(); });
        try {
            TestSuite.createMechanicalTrainingFixtures(root.resolve("training"));
            Path input = root.resolve("event.wav"); TestSuite.writeEventWav(input);
            var store = new EventStore(root.resolve("events.csv"));
            SwingUtilities.invokeAndWait(() -> {
                DarkTheme.install();
                var dashboard = new Dashboard(root.resolve("training"), store, null);
                reference.set(dashboard); dashboard.setVisible(true);
            });
            waitFor(() -> !reference.get().isBusy() && reference.get().hasClassifier(), "training loads off EDT");
            SwingUtilities.invokeAndWait(() -> reference.get().startWav(input));
            waitFor(() -> !reference.get().isBusy() && reference.get().eventCount() == 1, "GUI WAV-to-event integration");
            SwingUtilities.invokeAndWait(() -> reference.get().classifyWav(input));
            waitFor(() -> !reference.get().isBusy() && reference.get().eventCount() == 2, "unseen clip classification");
            SwingUtilities.invokeAndWait(() -> TestSuite.check(reference.get().predictionText().contains("Nearest standardized distance"), "prediction scores visible"));
            SwingUtilities.invokeAndWait(() -> reference.get().classifyWav(root.resolve("missing.wav")));
            waitFor(() -> !reference.get().isBusy() && reference.get().predictionText().contains("failed"), "invalid WAV error and control recovery");
            SwingUtilities.invokeAndWait(() -> reference.get().classifyWav(input));
            waitFor(() -> !reference.get().isBusy() && reference.get().eventCount() == 3, "classification recovers after error");
            SwingUtilities.invokeAndWait(() -> reference.get().classifyWav(root.resolve("training/scream_distress/mechanics-only.wav")));
            waitFor(() -> !reference.get().isBusy() && reference.get().eventCount() == 4, "known-fixture priority alert routing");
            SwingUtilities.invokeAndWait(() -> TestSuite.check(reference.get().predictionText().contains("Exact feature match"), "training-match diagnostic"));
            Path holdout = root.resolve("held-out");
            Files.createDirectories(holdout.resolve("explosion")); Files.copy(input, holdout.resolve("explosion/unseen-mechanics.wav"));
            SwingUtilities.invokeAndWait(() -> ((omniear.ui.WavTestingPanel) find(reference.get(), JTabbedPane.class).getComponentAt(1)).evaluate(holdout));
            waitFor(() -> !reference.get().isBusy() && reference.get().predictionText().contains("Evaluated: 1"), "held-out folder GUI evaluation");
            Robot robot = new Robot(); robot.setAutoDelay(100); robot.waitForIdle();
            Files.createDirectories(Path.of("build"));
            // Paint our displayed Swing window, rather than Robot screen capture, which
            // can block on a Wayland portal permission dialog and capture unrelated apps.
            for (int index = 0; index < 4; index++) {
                int tab = index;
                var render = new AtomicReference<java.awt.image.BufferedImage>();
                SwingUtilities.invokeAndWait(() -> {
                    var frame = reference.get();
                    JTabbedPane tabs = find(frame, JTabbedPane.class);
                    TestSuite.check(tabs.getTabCount() == 4, "four workflow tabs");
                    tabs.setSelectedIndex(tab); frame.validate();
                    var image = new java.awt.image.BufferedImage(frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    try { frame.printAll(graphics); } finally { graphics.dispose(); }
                    render.set(image);
                });
                ImageIO.write(render.get(), "png", Path.of("build/ui-tab-" + tab + ".png").toFile());
            }
            // KDE/Wayland may deny programmatic focus stealing. A real click is
            // required before Robot keyboard input, even after requestFocus().
            var focusPoint = new AtomicReference<Point>();
            Point pointer = MouseInfo.getPointerInfo().getLocation();
            for (int attempt = 0; attempt < 5; attempt++) {
                SwingUtilities.invokeAndWait(() -> {
                    var frame = reference.get(); frame.setAlwaysOnTop(true); frame.toFront(); frame.requestFocus();
                    find(frame, JTabbedPane.class).requestFocusInWindow();
                });
                // Window-manager raise/placement is asynchronous, outside the Swing queue.
                robot.waitForIdle(); robot.delay(200);
                SwingUtilities.invokeAndWait(() -> {
                    Point point = find(reference.get(), JTabbedPane.class).getLocationOnScreen(); point.translate(30, 20); focusPoint.set(point);
                });
                robot.mouseMove(focusPoint.get().x, focusPoint.get().y);
                robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                robot.waitForIdle(); robot.delay(100);
                if (reference.get().isActive()) break;
            }
            waitFor(() -> reference.get().isActive(), "desktop grants keyboard focus to test window");
            SwingUtilities.invokeAndWait(() -> reference.get().setAlwaysOnTop(false));
            robot.keyPress(java.awt.event.KeyEvent.VK_ALT); robot.keyPress(java.awt.event.KeyEvent.VK_2);
            robot.keyRelease(java.awt.event.KeyEvent.VK_2); robot.keyRelease(java.awt.event.KeyEvent.VK_ALT); robot.waitForIdle();
            waitFor(() -> find(reference.get(), JTabbedPane.class).getSelectedIndex() == 1, "keyboard tab navigation");
            robot.mouseMove(pointer.x, pointer.y);
            SwingUtilities.invokeAndWait(() -> {
                Dashboard frame = reference.get(); frame.setSize(frame.getMinimumSize());
                JTabbedPane tabs = find(frame, JTabbedPane.class); tabs.setSelectedIndex(0); frame.validate();
                TestSuite.check(find(tabs.getComponentAt(0), JTable.class).getVisibleRect().height >= 32, "event rows remain visible at minimum window size");
            });
            SwingUtilities.invokeAndWait(() -> {
                TestSuite.check(reference.get().isShowing(), "real dashboard visible");
                TestSuite.check(reference.get().eventCount() == 4, "real table has anomaly and clip events");
                reference.get().closeApplication();
            });
            waitFor(() -> !reference.get().isDisplayable(), "dashboard disposes");
            TestSuite.check(errors.isEmpty(), "no uncaught Swing/worker exceptions: " + errors);
            // Wait for asynchronous resource release before checking durable reload.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            boolean released = false;
            while (!released && System.nanoTime() < deadline) {
                try (var reopened = new EventStore(root.resolve("events.csv"))) {
                    TestSuite.check(reopened.history().size() == 4, "GUI events persisted and lock released"); released = true;
                } catch (java.io.IOException e) { Thread.sleep(20); }
            }
            TestSuite.check(released, "GUI resource cleanup");
            SwingUtilities.invokeAndWait(() -> {
                var untrained = new Dashboard(root.resolve("empty-training"), null, "Test: memory-only CSV mode");
                reference.set(untrained); untrained.setVisible(true);
            });
            waitFor(() -> !reference.get().isBusy(), "empty training readiness settles");
            SwingUtilities.invokeAndWait(() -> {
                TestSuite.check(!reference.get().hasClassifier() && find(reference.get(), JLabel.class,
                        label -> label.getText() != null && label.getText().contains("UNTRAINED")) != null, "untrained mode explicitly identified");
                TestSuite.check(!find(reference.get(), JButton.class, button -> "Classify WAV…".equals(button.getText())).isEnabled()
                        && !find(reference.get(), JButton.class, button -> "Classify example".equals(button.getText())).isEnabled(), "untrained clip and demo prediction disabled");
                TestSuite.check(find(reference.get(), JButton.class, button -> "Start monitoring".equals(button.getText())).isEnabled()
                        && find(reference.get(), JLabel.class, label -> label.getText() != null && label.getText().contains("CSV PERSISTENCE DISABLED")) != null,
                        "anomaly-only monitoring remains available with explicit memory-only status");
                reference.get().closeApplication();
            });
            System.out.println("PASS: real-display Swing smoke test; four tabs, unseen clip, error recovery, keyboard navigation, persistence; build/ui-tab-*.png");
        } finally {
            if (reference.get() != null) SwingUtilities.invokeAndWait(() -> reference.get().closeApplication());
            TestSuite.deleteTree(root);
        }
    }
    private static void waitFor(BooleanSupplier predicate, String name) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            var ready = new AtomicReference<Boolean>(false);
            SwingUtilities.invokeAndWait(() -> ready.set(predicate.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out: " + name);
    }
}
