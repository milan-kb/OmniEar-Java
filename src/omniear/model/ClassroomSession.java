package omniear.model;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import omniear.alerts.EventStore;
import omniear.detection.DetectionPipeline;

/** Owns an isolated bundled model and append-only rehearsal CSVs; never deletes user data. */
public final class ClassroomSession implements AutoCloseable {
    private final Path directory;
    private final SoundClassifier classifier;
    private EventStore store;
    private boolean closed;

    public ClassroomSession(Path dataRoot) throws IOException {
        Path parent = dataRoot.resolve("classroom-demo");
        Files.createDirectories(parent);
        directory = Files.createTempDirectory(parent, "session-");
        Path training = directory.resolve("training");
        TrainingRepository.installBundled(training);
        var report = new TrainingRepository(training).load();
        if (!report.problems().isEmpty()) throw new IOException("Bundled training: " + report.problems());
        classifier = report.classifier().orElseThrow(() -> new IOException("Bundled model unavailable; rebuild the JAR"));
        store = new EventStore(nextCsv());
    }
    private Path nextCsv() { return directory.resolve("events-" + UUID.randomUUID() + ".csv"); }
    public synchronized Path csvPath() { return store.path(); }
    public synchronized SoundEvent classify(float[] audio, String source) throws IOException {
        if (closed) throw new IOException("Demo session closed");
        var event = DetectionPipeline.classifyClip(audio, classifier, Instant.now(), "Prerecorded classroom WAV: " + source);
        store.append(event);
        return event;
    }
    public synchronized void reset() throws IOException {
        if (closed) throw new IOException("Demo session closed");
        var fresh = new EventStore(nextCsv());
        try { store.close(); } catch (IOException e) { fresh.close(); throw e; }
        store = fresh;
    }
    @Override public synchronized void close() throws IOException {
        closed = true;
        store.close();
    }
}
