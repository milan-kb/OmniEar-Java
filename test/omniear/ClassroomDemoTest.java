package omniear;

import java.nio.file.*;
import java.util.*;
import omniear.alerts.*;
import omniear.model.*;

/** Real selected WAVs and real inference; selection is not an accuracy benchmark. */
public final class ClassroomDemoTest {
    private ClassroomDemoTest() { }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("omniear-classroom-test-");
        try {
            Path existing = root.resolve("events.csv"); Files.writeString(existing, "keep normal history");
            Path training = root.resolve("training"); Files.createDirectories(training);
            Files.writeString(training.resolve("user-note.txt"), "keep custom training");
            Path firstCsv;
            try (var session = new ClassroomSession(root)) {
                var samples = DemoSample.classroom();
                TestSuite.check(samples.size() == 5 && new HashSet<>(samples.stream().map(DemoSample::label).toList()).size() == 5, "one curated recording per category");
                firstCsv = session.csvPath();
                System.out.println("file\tactual_prediction\tpriority\tstatus\tvote\tdistance");
                for (int repeat = 0; repeat < 2; repeat++) for (var sample : samples) {
                    var event = session.classify(sample.load(), sample.fileName());
                    TestSuite.check(event.category() == sample.label(), "real classroom label: " + sample.fileName());
                    TestSuite.check(event.isAlert() == (sample.label() != Category.BACKGROUND), "existing alert policy: " + sample.fileName());
                    TestSuite.check(event.priority() == sample.label().priority(), "real severity: " + sample.fileName());
                    TestSuite.check(event.nearestDistance() > 1e-9, "not an exact training match");
                    System.out.printf(Locale.ROOT, "%s\t%s\t%s\t%s\t%.6f\t%.6f%n", sample.fileName(), event.category().id(), event.priority(), event.status(), event.voteShare(), event.nearestDistance());
                }
                session.reset();
                TestSuite.check(!firstCsv.equals(session.csvPath()), "reset creates isolated new CSV");
                try (var previous = new EventStore(firstCsv)) { TestSuite.check(previous.history().size() == 10, "reset preserves and releases previous history"); }
                TestSuite.check(session.classify(samples.getLast().load(), "background after reset").status() == SoundEvent.Status.BACKGROUND_SUPPRESSED, "background remains suppressed after reset");
            }
            try (var reopened = new EventStore(firstCsv)) { TestSuite.check(reopened.history().size() == 10, "all repeated results survive restart"); }
            try (var restarted = new ClassroomSession(root)) { TestSuite.check(!firstCsv.equals(restarted.csvPath()), "fresh launch cannot reuse custom or stale demo session"); }
            TestSuite.check(Files.readString(existing).equals("keep normal history") && Files.readString(training.resolve("user-note.txt")).equals("keep custom training"), "demo never touches normal data");
            System.out.println("PASS: five genuine classroom outcomes, repeats, reset, CSV reopen, fresh startup and isolation");
        } finally { TestSuite.deleteTree(root); }
    }
}
