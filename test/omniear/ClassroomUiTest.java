package omniear;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import omniear.alerts.EventStore;
import omniear.model.*;
import omniear.ui.*;
import static omniear.SwingTestSupport.*;

/** Real Swing, real bundled classifier, real Java Sound output. Requires desktop and audio output. */
public final class ClassroomUiTest {
    private ClassroomUiTest() { }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("omniear-classroom-ui-");
        var reference = new AtomicReference<ClassroomDemo>();
        try {
            open(reference, root);
            var frame = reference.get();
            var robot = new Robot(); robot.setAutoDelay(100);
            SwingUtilities.invokeAndWait(() -> { frame.setSize(frame.getMinimumSize()); frame.toFront(); });
            String[] buttons = {"1 · Scream", "2 · Explosion", "3 · Impact", "4 · Siren", "5 · Background"};
            if (args.length > 0 && args[0].equals("--output-unavailable")) {
                SwingUtilities.invokeAndWait(() -> find(frame, JButton.class, b -> b.getText().equals(buttons[0])).doClick());
                waitIdle(frame, 15);
                SwingUtilities.invokeAndWait(() -> {
                    TestSuite.check(frame.eventCount() == 1 && frame.resultText().contains("Playback unavailable") && frame.resultText().contains("P0"), "output failure is explicit and keeps real persisted inference");
                    find(frame, JCheckBox.class, b -> true).doClick();
                    find(frame, JButton.class, b -> b.getText().equals(buttons[4])).doClick();
                });
                waitIdle(frame, 10);
                SwingUtilities.invokeAndWait(() -> TestSuite.check(frame.eventCount() == 2 && frame.resultText().contains("Silent classification") && frame.resultText().contains("BACKGROUND_SUPPRESSED"), "silent recovery after unavailable output"));
                Path saved = frame.csvPath(); SwingUtilities.invokeAndWait(frame::closeApplication); reopen(saved, 2);
                System.out.println("PASS: unavailable audio output is reported, predictions persist, silent recovery works");
                return;
            }
            var samples = DemoSample.classroom();
            for (int i = 0; i < buttons.length; i++) {
                final int count = i + 1; final String text = buttons[i];
                SwingUtilities.invokeAndWait(() -> {
                    JButton button = find(frame, JButton.class, b -> b.getText().equals(text));
                    TestSuite.check(button.isEnabled() && button.getVisibleRect().width == button.getWidth(), "one-click recording visible at minimum size");
                    button.doClick(); button.doClick(); // second click while busy must not enqueue a second event
                    TestSuite.check(!find(frame, JButton.class, b -> b.getText().equals("Reset Demo")).isEnabled(), "reset cannot race classification/playback");
                });
                waitIdle(frame, 45);
                if (i == 0) screenshot(frame, "build/classroom-layout.png");
                SwingUtilities.invokeAndWait(() -> {
                    TestSuite.check(frame.eventCount() == count, "one actual persisted event per click");
                    int height = find(frame, WaveformPanel.class, w -> true).getHeight();
                    TestSuite.check(height >= 70, "waveform remains visible at minimum window size; actual=" + height);
                    TestSuite.check(frame.resultText().contains("Playback complete"), "real output line played the complete WAV: " + frame.resultText());
                    System.out.println(frame.resultText());
                });
                if (i == 1) screenshot(frame, "build/classroom-alert.png");
            }
            SwingUtilities.invokeAndWait(() -> {
                TestSuite.check(frame.resultText().contains("BACKGROUND_SUPPRESSED") && frame.resultText().contains("No threat alert"), "background shows no threat alert");
                TestSuite.check(find(frame, JTable.class, t -> t.getRowCount() == 5) != null, "five outcomes visible in history");
            });
            screenshot(frame, "build/classroom-history.png");
            Path previous = frame.csvPath();
            // Exercise the actual reset button with native pointer input.
            var point = new AtomicReference<Point>();
            SwingUtilities.invokeAndWait(() -> {
                var button = find(frame, JButton.class, b -> b.getText().equals("Reset Demo"));
                Point p = button.getLocationOnScreen(); p.translate(button.getWidth() / 2, button.getHeight() / 2); point.set(p);
            });
            robot.mouseMove(point.get().x, point.get().y); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            TestSuite.check(awaitOnEdt(() -> !frame.isBusy() && frame.eventCount() == 0 && !frame.csvPath().equals(previous), 10, 25), "native Reset Demo click clears view and rotates CSV");
            try (var store = new EventStore(previous)) {
                TestSuite.check(store.history().size() == 5, "all five GUI results survive reset and CSV reopen");
                for (int i = 0; i < 5; i++) TestSuite.check(store.history().get(i).category() == samples.get(i).label(), "GUI uses real expected category");
            }
            // Silent recovery is still real inference, and speaker gain cannot affect it.
            robot.keyPress(KeyEvent.VK_ALT); robot.keyPress(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_A); robot.keyRelease(KeyEvent.VK_ALT);
            TestSuite.check(awaitOnEdt(() -> !find(frame, JCheckBox.class, b -> b.getText().equals("Play audio")).isSelected(), 5, 25), "keyboard shortcut toggles playback without microphone");
            SwingUtilities.invokeAndWait(() -> {
                find(frame, JSlider.class, s -> true).setValue(0);
                find(frame, JButton.class, b -> b.getText().equals(buttons[0])).doClick();
            });
            waitIdle(frame, 10);
            SwingUtilities.invokeAndWait(() -> TestSuite.check(frame.eventCount() == 1 && frame.resultText().contains("Silent classification") && frame.resultText().contains("P0"), "silent recovery uses real classification and alert"));
            // Stop real output; keep one actual result, then replay the same file successfully.
            SwingUtilities.invokeAndWait(() -> {
                find(frame, JCheckBox.class, b -> true).doClick();
                find(frame, JButton.class, b -> b.getText().equals(buttons[3])).doClick();
            });
            TestSuite.check(awaitOnEdt(() -> frame.eventCount() == 2 && frame.resultText().contains("Playing prerecorded"), 10, 25), "siren begins output after persisting result");
            SwingUtilities.invokeAndWait(() -> find(frame, JButton.class, b -> b.getText().equals("Stop playback")).doClick());
            waitIdle(frame, 10);
            SwingUtilities.invokeAndWait(() -> TestSuite.check(frame.eventCount() == 2 && frame.resultText().contains("Playback stopped"), "stop releases device without duplicating or discarding the result"));
            SwingUtilities.invokeAndWait(() -> find(frame, JButton.class, b -> b.getText().equals(buttons[2])).doClick());
            waitIdle(frame, 10);
            SwingUtilities.invokeAndWait(() -> TestSuite.check(frame.eventCount() == 3 && frame.resultText().contains("Playback complete"), "output can reopen after stop"));
            Path latest = frame.csvPath();
            SwingUtilities.invokeAndWait(frame::closeApplication);
            reopen(latest, 3);
            open(reference, root);
            var restarted = reference.get();
            SwingUtilities.invokeAndWait(() -> TestSuite.check(restarted.eventCount() == 0 && !restarted.csvPath().equals(latest), "new GUI startup uses fresh isolated session"));
            // Closing during active output must release worker/device/CSV.
            SwingUtilities.invokeAndWait(() -> find(restarted, JButton.class, b -> b.getText().equals(buttons[3])).doClick());
            TestSuite.check(awaitOnEdt(() -> restarted.eventCount() == 1, 10, 25), "restart inference persists");
            Path closing = restarted.csvPath();
            SwingUtilities.invokeAndWait(restarted::closeApplication);
            reopen(closing, 1);
            System.out.println("PASS: classroom GUI, five real playbacks/outcomes, double-click guard, native reset, silent recovery, stop/replay, close/restart and CSV persistence");
        } finally {
            if (reference.get() != null) SwingUtilities.invokeAndWait(reference.get()::closeApplication);
            TestSuite.deleteTree(root);
        }
    }
    private static void open(AtomicReference<ClassroomDemo> reference, Path root) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DarkTheme.install();
            try { var frame = new ClassroomDemo(root); reference.set(frame); frame.setVisible(true); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
        });
        TestSuite.check(awaitOnEdt(() -> reference.get().isReady() && !reference.get().isBusy(), 30, 25), "fresh classroom startup loads bundled model");
    }
    private static void waitIdle(ClassroomDemo frame, int seconds) throws Exception {
        TestSuite.check(awaitOnEdt(() -> !frame.isBusy(), seconds, 25), "demo worker completes");
    }
    private static void reopen(Path csv, int count) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (true) {
            try (var store = new EventStore(csv)) { TestSuite.check(store.history().size() == count, "closed GUI releases durable CSV"); return; }
            catch (java.io.IOException e) { if (System.nanoTime() > deadline) throw e; Thread.sleep(25); }
        }
    }
    private static void screenshot(JFrame frame, String file) throws Exception {
        var image = new AtomicReference<java.awt.image.BufferedImage>();
        SwingUtilities.invokeAndWait(() -> {
            var rendered = new java.awt.image.BufferedImage(frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = rendered.createGraphics();
            try { frame.printAll(graphics); } finally { graphics.dispose(); }
            image.set(rendered);
        });
        Files.createDirectories(Path.of(file).getParent());
        javax.imageio.ImageIO.write(image.get(), "png", Path.of(file).toFile());
    }
}
