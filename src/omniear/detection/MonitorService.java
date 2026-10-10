package omniear.detection;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.*;
import omniear.audio.*;
import omniear.alerts.EventStore;
import omniear.model.*;

/** Owns the single audio worker; UI callbacks must marshal onto Swing's EDT. */
public final class MonitorService implements AutoCloseable {
    public interface Listener {
        void onBlock(float[] samples, AdaptiveDetector.Reading reading, double audioSeconds);
        void onEvent(SoundEvent event);
        void onState(boolean running, String message);
        void onError(String message, Exception error);
    }
    private final Listener listener;
    private final EventStore store; // null only when persistence failed to open, reported by UI
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "omniear-audio"); thread.setDaemon(true); return thread;
    });
    private volatile AudioSource source;
    private volatile Thread reader;
    private volatile boolean stopping;
    private boolean running, closed;

    public MonitorService(Listener listener, EventStore store) { this.listener = java.util.Objects.requireNonNull(listener); this.store = store; }

    public synchronized void start(AudioSource input, AdaptiveDetector.Config config, SoundClassifier classifier) {
        if (closed || running) throw new IllegalStateException("Stop the active source before starting another");
        source = input; stopping = false; running = true;
        listener.onState(true, "Opening " + input.description());
        worker.submit(() -> run(input, config, classifier));
    }

    private void run(AudioSource input, AdaptiveDetector.Config config, SoundClassifier classifier) {
        reader = Thread.currentThread();
        String completion = "Stopped";
        try (input) {
            if (stopping) return;
            input.open();
            var pipeline = new DetectionPipeline(config, classifier, Instant.now(), input.description());
            listener.onState(true, "Listening — calibrating for the first 2 seconds");
            float[] buffer = new float[AudioSamples.BLOCK_SIZE];
            while (!stopping) {
                int count = input.read(buffer);
                if (stopping) break;
                if (count == 0) {
                    pipeline.finish().ifPresent(this::publish);
                    completion = "WAV analysis complete";
                    break;
                }
                if (input instanceof WavSource && pipeline.audioSeconds() + count / 16000.0 > WavSource.MAX_SECONDS) throw new IOException("WAV duration limit exceeded");
                float[] block = Arrays.copyOf(buffer, count);
                // ponytail: inference/CSV share this worker; split into a bounded executor if
                // measured stalls overrun the half-second Java Sound input buffer.
                pipeline.accept(block).ifPresent(this::publish);
                listener.onBlock(block, pipeline.lastReading(), pipeline.audioSeconds());
            }
        } catch (Exception e) {
            if (!stopping) { completion = "Audio failed — see diagnostics"; listener.onError("Audio processing failed", e); }
        } finally {
            reader = null;
            synchronized (this) { running = false; source = null; }
            listener.onState(false, completion);
            Thread.interrupted();
        }
    }

    private void publish(SoundEvent event) {
        if (stopping) return;
        if (store != null) {
            try { store.append(event); } catch (IOException e) { listener.onError("Event shown but NOT saved to CSV", e); }
        }
        listener.onEvent(event);
    }

    public synchronized void stop() {
        stopping = true;
        Thread current = reader;
        if (current != null) current.interrupt();
        AudioSource currentSource = source;
        if (currentSource != null) {
            Thread.ofPlatform().daemon().name("omniear-close-audio").start(() -> {
                try { currentSource.close(); } catch (IOException e) { listener.onError("Audio cleanup failed", e); }
            });
        }
    }
    public synchronized boolean isRunning() { return running; }
    @Override public synchronized void close() { closed = true; stop(); worker.shutdown(); }
    public boolean awaitTermination(long seconds) throws InterruptedException { return worker.awaitTermination(seconds, TimeUnit.SECONDS); }
}
