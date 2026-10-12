package omniear;

import omniear.audio.InputDiagnostics;

public final class InputDiagnosticsTest {
    private InputDiagnosticsTest() { }
    public static void main(String[] args) {
        var level = new InputDiagnostics();
        TestSuite.check(level.summary().contains("No samples"), "empty input is not a working microphone");
        level.add(new float[]{0, 0});
        TestSuite.check(level.summary().contains("silence") && level.summary().contains("Very quiet"), "silence has actionable guidance");
        float[] block = {1, -1, .5f, -.5f};
        level.add(block);
        TestSuite.check(Math.abs(level.rms() - Math.sqrt(2.5 / 6)) < 1e-9 && level.peak() == 1, "levels aggregate samples, not block averages");
        TestSuite.check(Math.abs(level.nearFullScalePercent() - 100.0 / 3) < 1e-9 && level.summary().contains("Possible clipping"), "both rails counted across unequal blocks");
        TestSuite.check(block[0] == 1 && block[3] == -.5f, "diagnostics do not change gain/samples");
        var normal = new InputDiagnostics(); normal.add(new float[]{.1f, -.1f});
        TestSuite.check(normal.summary().contains("-20.0 dBFS") && normal.summary().contains("Signal received"), "dBFS reference is unit amplitude");
    }
}
