import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import omniear.audio.*;
import omniear.detection.*;
import omniear.model.*;

/** Actual stage trace for each inspected demo, usable against baseline or repaired classes. */
class AcousticTrace {
    public static void main(String[] args) throws Exception {
        var model = new TrainingRepository(Path.of(args[0])).load().classifier().orElseThrow();
        System.out.println("file\ttruth\tseconds\tdirect\tvote\tdistance\tgate\tonsets\twarmup_blocks_above_gate\tcalibrated_blocks_above_gate\tmax_rms\tmax_threshold\tevents\talerts\tstream_outcomes");
        for (var sample : DemoSample.all()) {
            float[] audio = sample.load();
            var direct = DetectionPipeline.classifyClip(audio, model, Instant.EPOCH, sample.fileName());
            var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), model, Instant.EPOCH, sample.fileName());
            int onsets = 0, blind = 0, eligible = 0;
            double maxRms = 0, maxThreshold = 0;
            var events = new ArrayList<SoundEvent>();
            for (int i = 0; i < audio.length; i += 1600) {
                pipeline.accept(Arrays.copyOfRange(audio, i, Math.min(audio.length, i + 1600))).ifPresent(events::add);
                var r = pipeline.lastReading();
                if (r.triggered()) onsets++;
                if (r.rms() > r.threshold()) { if (r.calibrated()) eligible++; else blind++; }
                maxRms = Math.max(maxRms, r.rms()); maxThreshold = Math.max(maxThreshold, r.threshold());
            }
            pipeline.finish().ifPresent(events::add);
            System.out.printf(Locale.ROOT, "%s\t%s\t%.4f\t%s\t%.6f\t%.6f\t%s\t%d\t%d\t%d\t%.6f\t%.6f\t%d\t%d\t%s%n",
                    sample.fileName(), sample.label().id(), audio.length / 16000., direct.category().id(), direct.voteShare(),
                    direct.nearestDistance(), direct.status(), onsets, blind, eligible, maxRms, maxThreshold, events.size(),
                    events.stream().filter(SoundEvent::isAlert).count(),
                    events.stream().map(e -> String.format(Locale.ROOT, "%.3f:%s:%s", e.audioSeconds(), e.category().id(), e.status())).toList());
        }
    }
}
