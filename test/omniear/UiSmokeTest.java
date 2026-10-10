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
                var dashboard = new Dashboard(root.resolve("training"), store, null);
                reference.set(dashboard); dashboard.setVisible(true);
            });
            waitFor(() -> !reference.get().isBusy() && reference.get().hasClassifier(), "training loads off EDT");
            SwingUtilities.invokeAndWait(() -> reference.get().startWav(input));
            waitFor(() -> !reference.get().isBusy() && reference.get().eventCount() == 1, "GUI WAV-to-event integration");
            Robot robot = new Robot(); robot.waitForIdle();
            var render = new AtomicReference<java.awt.image.BufferedImage>();
            // Paint our displayed Swing window, rather than Robot screen capture, which
            // can block on a Wayland portal permission dialog and capture unrelated apps.
            SwingUtilities.invokeAndWait(() -> {
                var frame = reference.get();
                var image = new java.awt.image.BufferedImage(frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try { frame.printAll(graphics); } finally { graphics.dispose(); }
                render.set(image);
            });
            Files.createDirectories(Path.of("build"));
            ImageIO.write(render.get(), "png", Path.of("build/ui-smoke.png").toFile());
            SwingUtilities.invokeAndWait(() -> {
                TestSuite.check(reference.get().isShowing(), "real dashboard visible");
                TestSuite.check(reference.get().eventCount() == 1, "real table has detected event");
                reference.get().closeApplication();
            });
            waitFor(() -> !reference.get().isDisplayable(), "dashboard disposes");
            TestSuite.check(errors.isEmpty(), "no uncaught Swing/worker exceptions: " + errors);
            // Wait for asynchronous resource release before checking durable reload.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
            boolean released = false;
            while (!released && System.nanoTime() < deadline) {
                try (var reopened = new EventStore(root.resolve("events.csv"))) {
                    TestSuite.check(reopened.history().size() == 1, "GUI event persisted and lock released"); released = true;
                } catch (java.io.IOException e) { Thread.sleep(20); }
            }
            TestSuite.check(released, "GUI resource cleanup");
            System.out.println("PASS: real-display Swing smoke test; component render build/ui-smoke.png");
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
