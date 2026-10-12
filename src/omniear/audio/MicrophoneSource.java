package omniear.audio;

import java.io.IOException;
import javax.sound.sampled.*;

/** Laptop default input only; common native rates are tried without external libraries. */
public final class MicrophoneSource extends PcmSource {
    private volatile TargetDataLine line;
    @Override public String description() { return "OS default recording input"; }

    @Override protected AudioInputStream openStream() throws IOException {
        Exception last = null;
        for (int rate : new int[]{16000, 48000, 44100}) {
            for (int channels : new int[]{1, 2}) {
                if (isClosed()) throw new IOException("Microphone opening cancelled");
                AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
                try {
                    TargetDataLine candidate = AudioSystem.getTargetDataLine(format);
                    line = candidate;
                    candidate.open(format, rate * channels * 2 / 2);
                    if (isClosed()) { candidate.close(); throw new IOException("Microphone opening cancelled"); }
                    candidate.start();
                    return new AudioInputStream(candidate);
                } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                    last = e;
                    if (line != null) line.close();
                }
            }
        }
        throw new IOException("Cannot open the microphone. Check OS permissions/default input, close competing apps, or import a WAV.", last);
    }

    @Override public void close() throws IOException {
        // Closing the line unblocks a blocking read; interruption alone does not.
        super.close();
        TargetDataLine current = line;
        if (current != null) { current.stop(); current.close(); }
    }
}
