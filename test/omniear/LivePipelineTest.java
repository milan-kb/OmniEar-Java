package omniear;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import omniear.detection.*;
import omniear.model.*;

/** Mechanics only: fixed predictions verify scheduling, never count as acoustic evidence. */
public final class LivePipelineTest {
    private LivePipelineTest() { }
    public static void main(String[] args) {
        var calls = new AtomicInteger();
        SoundClassifier model = features -> {
            calls.incrementAndGet();
            return new SoundClassifier.Prediction(Category.SIREN_TRAFFIC, 1, 0);
        };
        var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), model, Instant.EPOCH, "mechanics");
        var events = new ArrayList<SoundEvent>();
        for (int i = 0; i < 15; i++) pipeline.accept(TestSuite.constant(1600, .05f)).ifPresent(events::add);
        TestSuite.check(events.stream().anyMatch(SoundEvent::isAlert), "sustained signal at startup reaches classifier before old 2 s calibration");
        for (int i = 0; i < 85; i++) pipeline.accept(TestSuite.constant(1600, .05f)).ifPresent(events::add);
        TestSuite.check(calls.get() <= 20, "bounded classification rate, not every 100 ms block");
        TestSuite.check(events.stream().filter(SoundEvent::isAlert).count() == 1, "one alert per sustained category episode");
        for (int i = 0; i < 40; i++) pipeline.accept(new float[1600]);
        events.clear();
        for (int i = 0; i < 15; i++) pipeline.accept(TestSuite.constant(1600, .05f)).ifPresent(events::add);
        TestSuite.check(events.stream().filter(SoundEvent::isAlert).count() == 1, "new episode rearms after quiet interval");
        pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), model, Instant.EPOCH, "silence");
        calls.set(0);
        for (int i = 0; i < 100; i++) TestSuite.check(pipeline.accept(new float[1600]).isEmpty(), "silence emits no events");
        TestSuite.check(calls.get() == 0, "digital silence costs no DSP/classification");
        pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), model, Instant.EPOCH, "short EOF");
        pipeline.accept(TestSuite.constant(1234, .05f));
        TestSuite.check(pipeline.finish().isPresent(), "short partial-block WAV is classified at EOF");
        TestSuite.check(pipeline.finish().isEmpty(), "EOF is idempotent");
        pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(),
                f -> new SoundClassifier.Prediction(Category.SCREAM_DISTRESS, 1, 2), Instant.EPOCH, "unsupported");
        for (int i = 0; i < 9; i++) pipeline.accept(TestSuite.constant(1600, .05f));
        var unsupported = pipeline.accept(TestSuite.constant(1600, .05f)).orElseThrow();
        TestSuite.check(!unsupported.isAlert() && unsupported.status() == SoundEvent.Status.EVIDENCE_SUPPRESSED,
                "unanimous but distant neighbors do not become streaming alerts");
        TestSuite.check(unsupported.audioSeconds() == 1 && unsupported.timestamp().equals(Instant.EPOCH.plusSeconds(1)),
                "periodic decision timestamp is emission time, not an invented onset");
        System.out.println("PASS: live scheduling mechanics");
    }
}
