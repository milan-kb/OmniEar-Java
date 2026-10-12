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
            SwingUtilities.invokeAndWait(() -> ((omniear.ui.WavTestingPanel) findTabs(reference.get()).getComponentAt(1)).evaluate(holdout));
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
                    JTabbedPane tabs = findTabs(frame);
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
                    findTabs(frame).requestFocusInWindow();
                });
                // Window-manager raise/placement is asynchronous, outside the Swing queue.
                robot.waitForIdle(); robot.delay(200);
                SwingUtilities.invokeAndWait(() -> {
                    Point point = findTabs(reference.get()).getLocationOnScreen(); point.translate(30, 20); focusPoint.set(point);
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
            waitFor(() -> findTabs(reference.get()).getSelectedIndex() == 1, "keyboard tab navigation");
            robot.mouseMove(pointer.x, pointer.y);
            SwingUtilities.invokeAndWait(() -> {
                Dashboard frame = reference.get(); frame.setSize(frame.getMinimumSize());
                findTabs(frame).setSelectedIndex(0); frame.validate();
                TestSuite.check(findTable(findTabs(frame).getComponentAt(0)).getVisibleRect().height >= 32, "event rows remain visible at minimum window size");
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
                TestSuite.check(!reference.get().hasClassifier() && containsLabel(reference.get(), "UNTRAINED"), "untrained mode explicitly identified");
                TestSuite.check(!findButton(reference.get(), "Classify WAV…").isEnabled()
                        && !findButton(reference.get(), "Classify example").isEnabled(), "untrained clip and demo prediction disabled");
                TestSuite.check(findButton(reference.get(), "Start monitoring").isEnabled()
                        && containsLabel(reference.get(), "CSV PERSISTENCE DISABLED"), "anomaly-only monitoring remains available with explicit memory-only status");
                reference.get().closeApplication();
            });
            System.out.println("PASS: real-display Swing smoke test; four tabs, unseen clip, error recovery, keyboard navigation, persistence; build/ui-tab-*.png");
        } finally {
            if (reference.get() != null) SwingUtilities.invokeAndWait(() -> reference.get().closeApplication());
            TestSuite.deleteTree(root);
        }
    }
    private static JTabbedPane findTabs(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JTabbedPane tabs) return tabs;
            if (child instanceof Container container) { JTabbedPane tabs = findTabs(container); if (tabs != null) return tabs; }
        }
        return null;
    }
    private static JTable findTable(Component parent) {
        if (parent instanceof JTable table) return table;
        if (parent instanceof Container container) for (Component child : container.getComponents()) {
            JTable table = findTable(child); if (table != null) return table;
        }
        return null;
    }
    private static JButton findButton(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return button;
            if (child instanceof Container container) { var found = findButton(container, text); if (found != null) return found; }
        }
        return null;
    }
    private static boolean containsLabel(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && label.getText() != null && label.getText().contains(text)) return true;
            if (child instanceof Container container && containsLabel(container, text)) return true;
        }
        return false;
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
