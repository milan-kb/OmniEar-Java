package omniear.audio;

import java.util.Locale;

/** Read-only level diagnostics on decoded mono samples; never applies gain or changes detection. */
public final class InputDiagnostics {
    private double squares, peak;
    private long samples, nearFullScale;

    public void add(float[] block) {
        for (float sample : block) {
            if (!Float.isFinite(sample)) throw new IllegalArgumentException("Nonfinite input level");
            squares += (double) sample * sample;
            peak = Math.max(peak, Math.abs(sample));
            if (Math.abs(sample) >= .99) nearFullScale++;
        }
        samples += block.length;
    }
    public double rms() { return samples == 0 ? 0 : Math.sqrt(squares / samples); }
    public double peak() { return peak; }
    public double nearFullScalePercent() { return samples == 0 ? 0 : 100.0 * nearFullScale / samples; }
    public String summary() {
        String level = rms() == 0 ? "silence" : String.format(Locale.ROOT, "%.1f dBFS", 20 * Math.log10(rms()));
        String advice = samples == 0 ? "No samples received"
                : nearFullScale > 0 ? "Possible clipping: lower OS input gain, then retest"
                : peak < .001 ? "Very quiet: check OS default input, mute and gain; speak normally and retest"
                : "Signal received; gain is controlled in OS sound settings";
        return String.format(Locale.ROOT, "RMS %.4f (%s) · peak %.4f · near full scale %.2f%% · %s",
                rms(), level, peak, nearFullScalePercent(), advice);
    }
}
