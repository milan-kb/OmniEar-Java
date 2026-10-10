package omniear.audio;

import java.io.IOException;
import javax.sound.sampled.*;

/** Common PCM decoder and streaming anti-aliasing resampler for file and mic input. */
public abstract class PcmSource implements AudioSource {
    private volatile boolean closed;
    private AudioInputStream stream;
    private AudioFormat format;
    private byte[] bytes;
    private int bytePosition;
    private int byteCount;
    private final float[] history = new float[2048];
    private long inputCount;
    private long outputCount;
    private long decodedFrames;
    private boolean eof;
    private double ratio;
    private double cutoff;
    private int radius;

    protected abstract AudioInputStream openStream() throws IOException;
    protected final boolean isClosed() { return closed; }

    @Override public final void open() throws IOException {
        if (closed) throw new IOException("Audio source already closed");
        AudioInputStream opened = openStream();
        try {
            AudioFormat f = opened.getFormat();
            boolean integer = f.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)
                    || f.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED);
            boolean floating = f.getEncoding().equals(AudioFormat.Encoding.PCM_FLOAT);
            int bits = f.getSampleSizeInBits();
            if ((!integer && !floating) || (integer && bits != 8 && bits != 16 && bits != 24 && bits != 32)
                    || (floating && bits != 32 && bits != 64) || f.getChannels() < 1 || f.getChannels() > 8
                    || !Float.isFinite(f.getSampleRate()) || f.getSampleRate() < 8000 || f.getSampleRate() > 192000
                    || f.getFrameSize() != f.getChannels() * (bits / 8)) {
                throw new IOException("Unsupported audio: " + f + ". Use PCM WAV (8–32 bit or float), 8–192 kHz, 1–8 channels.");
            }
            synchronized (this) {
                if (closed) throw new IOException("Audio source cancelled while opening");
                if (stream != null) throw new IOException("Audio source already open");
                format = f;
                ratio = (double) f.getSampleRate() / AudioSamples.SAMPLE_RATE;
                cutoff = .94 * Math.min(1, 1 / ratio);
                radius = (int) Math.ceil(32 / cutoff);
                bytes = new byte[f.getFrameSize() * 1024];
                stream = opened;
            }
        } catch (IOException | RuntimeException e) {
            opened.close();
            throw e;
        }
    }

    @Override public final int read(float[] destination) throws IOException {
        if (stream == null) throw new IOException("Audio source not open");
        if (destination.length == 0) throw new IllegalArgumentException("Empty destination");
        int count = 0;
        while (count < destination.length && !closed && !Thread.currentThread().isInterrupted()) {
            if (ratio == 1) {
                Float sample = nextMono();
                if (sample == null) break;
                destination[count++] = sample;
            } else {
                double position = outputCount * ratio;
                long center = (long) Math.floor(position);
                while (!eof && inputCount <= center + radius) {
                    Float sample = nextMono();
                    if (sample == null) eof = true;
                    else { history[(int) (inputCount % history.length)] = sample; inputCount++; }
                }
                if (eof && position >= inputCount) break;
                double value = 0, weights = 0;
                for (long index = center - radius + 1; index <= center + radius; index++) {
                    double distance = index - position;
                    if (Math.abs(distance) >= radius) continue;
                    double x = Math.PI * cutoff * distance;
                    double weight = cutoff * (Math.abs(x) < 1e-12 ? 1 : Math.sin(x) / x)
                            * (.5 + .5 * Math.cos(Math.PI * distance / radius));
                    weights += weight;
                    if (index >= 0 && index < inputCount) value += weight * history[(int) (index % history.length)];
                }
                destination[count++] = (float) Math.clamp(value / weights, -1, 1);
                outputCount++;
            }
        }
        return count;
    }

    private Float nextMono() throws IOException {
        if (bytePosition >= byteCount) {
            byteCount = stream.read(bytes);
            bytePosition = 0;
            if (byteCount < 0) {
                if (stream.getFrameLength() != AudioSystem.NOT_SPECIFIED && decodedFrames < stream.getFrameLength()) throw new IOException("Truncated WAV PCM data");
                return null;
            }
            if (byteCount == 0 || byteCount % format.getFrameSize() != 0) throw new IOException("Incomplete PCM frame");
        }
        int width = format.getSampleSizeInBits() / 8;
        double mono = 0;
        for (int channel = 0; channel < format.getChannels(); channel++) {
            long raw = 0;
            for (int j = 0; j < width; j++) {
                int offset = format.isBigEndian() ? j : width - 1 - j;
                raw = (raw << 8) | (bytes[bytePosition + channel * width + offset] & 0xffL);
            }
            double value;
            if (format.getEncoding().equals(AudioFormat.Encoding.PCM_FLOAT)) {
                value = width == 4 ? Float.intBitsToFloat((int) raw) : Double.longBitsToDouble(raw);
                if (!Double.isFinite(value)) throw new IOException("Nonfinite floating-point audio");
                value = Math.clamp(value, -1, 1);
            } else {
                long midpoint = 1L << (format.getSampleSizeInBits() - 1);
                if (format.getEncoding().equals(AudioFormat.Encoding.PCM_UNSIGNED)) raw -= midpoint;
                else if (raw >= midpoint) raw -= midpoint * 2;
                value = (double) raw / midpoint;
            }
            mono += value;
        }
        bytePosition += format.getFrameSize();
        decodedFrames++;
        return (float) (mono / format.getChannels());
    }

    @Override public void close() throws IOException {
        AudioInputStream current;
        synchronized (this) { closed = true; current = stream; }
        if (current != null) current.close();
    }
}
