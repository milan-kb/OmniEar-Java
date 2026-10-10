package omniear.audio;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import javax.sound.sampled.*;

public final class WavSource extends PcmSource {
    public static final int MAX_SECONDS = 120;
    private final Path path;

    public WavSource(Path path) { this.path = java.util.Objects.requireNonNull(path); }
    @Override public String description() { return path.getFileName().toString(); }

    @Override protected AudioInputStream openStream() throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("WAV file not found: " + path);
        try {
            AudioFileFormat metadata = AudioSystem.getAudioFileFormat(path.toFile());
            if (!metadata.getType().equals(AudioFileFormat.Type.WAVE)) throw new IOException("Select a WAV file, not " + metadata.getType());
            float rate = metadata.getFormat().getSampleRate();
            if (metadata.getFrameLength() > rate * MAX_SECONDS) throw new IOException("WAV exceeds " + MAX_SECONDS + " seconds; trim it first.");
            return AudioSystem.getAudioInputStream(path.toFile());
        } catch (UnsupportedAudioFileException | IllegalArgumentException e) {
            throw new IOException("Invalid or unsupported WAV: " + path.getFileName(), e);
        }
    }

    /** Used only for bounded training/import clips; monitoring remains streaming. */
    public static float[] load(Path path) throws IOException {
        float[] all = new float[AudioSamples.SAMPLE_RATE * MAX_SECONDS];
        int total = 0;
        try (var source = new WavSource(path)) {
            source.open();
            float[] block = new float[AudioSamples.BLOCK_SIZE];
            int count;
            while ((count = source.read(block)) > 0) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("WAV import cancelled");
                if (total + count > all.length) throw new IOException("WAV exceeds duration limit");
                System.arraycopy(block, 0, all, total, count);
                total += count;
            }
        }
        if (total < AudioSamples.BLOCK_SIZE) throw new IOException("WAV must contain at least 100 ms of audio");
        return Arrays.copyOf(all, total);
    }
}
