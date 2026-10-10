package omniear.audio;

import java.io.IOException;

/** Blocking reads of normalized 16 kHz mono; one reader, close may be concurrent. */
public interface AudioSource extends AutoCloseable {
    void open() throws IOException;
    int read(float[] destination) throws IOException;
    String description();
    @Override void close() throws IOException;
}
