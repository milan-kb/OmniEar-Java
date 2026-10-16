package omniear.audio;

import java.io.InterruptedIOException;
import javax.sound.sampled.*;

/** One playback operation on a worker thread. Speaker gain never changes classifier input. */
public final class WavPlayback implements AutoCloseable {
    private volatile SourceDataLine line;
    private volatile boolean stopped;

    public boolean play(float[] samples, double gain) throws LineUnavailableException, InterruptedIOException {
        if (!Double.isFinite(gain) || gain < 0 || gain > 1) throw new IllegalArgumentException("Speaker gain must be 0–1");
        var format = new AudioFormat(16000, 16, 1, true, false);
        SourceDataLine output = AudioSystem.getSourceDataLine(format);
        synchronized (this) {
            if (stopped) { output.close(); return false; }
            line = output;
        }
        try {
            output.open(format, 6400);
            if (stopped) return false;
            output.start();
            byte[] bytes = new byte[3200];
            for (int offset = 0; offset < samples.length && !stopped; offset += 1600) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Playback interrupted");
                int count = Math.min(1600, samples.length - offset);
                for (int i = 0; i < count; i++) {
                    int value = (int) Math.round(Math.clamp(samples[offset + i] * gain, -1, 1) * 32767);
                    bytes[2 * i] = (byte) value; bytes[2 * i + 1] = (byte) (value >> 8);
                }
                int written = 0;
                while (written < count * 2 && !stopped) {
                    int n = output.write(bytes, written, count * 2 - written);
                    if (n == 0 && !stopped) throw new InterruptedIOException("Audio output stopped accepting samples");
                    written += n;
                }
            }
            if (!stopped) output.drain();
            return !stopped;
        } finally { output.close(); line = null; }
    }
    @Override public synchronized void close() {
        stopped = true;
        SourceDataLine output = line;
        if (output != null) { output.stop(); output.flush(); output.close(); }
    }
}
