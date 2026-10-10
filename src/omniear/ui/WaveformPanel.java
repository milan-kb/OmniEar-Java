package omniear.ui;

import java.awt.*;
import javax.swing.*;

/** Real sample waveform, fixed full-scale amplitude; no simulated animation. */
public final class WaveformPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private float[] samples = new float[0];
    public WaveformPanel() {
        setPreferredSize(new Dimension(900, 85));
        setBackground(new Color(16, 24, 38));
        getAccessibleContext().setAccessibleName("Audio waveform, normalized full-scale amplitude");
    }
    public void setSamples(float[] samples) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Waveform updates require EDT");
        this.samples = samples.clone(); repaint();
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int left = 48, width = Math.max(1, getWidth() - left - 16), middle = getHeight() / 2;
            int amplitude = Math.max(1, middle - 24);
            g.setColor(new Color(62, 76, 95));
            for (int y : new int[]{middle - amplitude, middle, middle + amplitude}) g.drawLine(left, y, left + width, y);
            g.setColor(new Color(185, 198, 214));
            g.drawString("+1.0", 8, middle - amplitude + 5);
            g.drawString("0", 24, middle + 5);
            g.drawString("−1.0", 8, middle + amplitude + 5);
            g.drawString(String.format(java.util.Locale.ROOT, "%.3f s · actual PCM samples · full-scale amplitude", samples.length / 16000.0), left, getHeight() - 6);
            if (samples.length == 0) { g.drawString("Start the microphone or import a WAV", left + 16, middle - 12); return; }
            g.setColor(new Color(63, 224, 197));
            for (int x = 0; x < width; x++) {
                int from = x * samples.length / width;
                int to = Math.min(samples.length, Math.max(from + 1, (x + 1) * samples.length / width));
                float low = samples[from], high = low;
                for (int i = from + 1; i < to; i++) { low = Math.min(low, samples[i]); high = Math.max(high, samples[i]); }
                g.drawLine(left + x, middle - Math.round(high * amplitude), left + x, middle - Math.round(low * amplitude));
            }
        } finally { g.dispose(); }
    }
}
