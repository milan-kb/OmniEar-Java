import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import omniear.audio.*;
import omniear.detection.*;
import omniear.model.*;

/** Paired weak-label replay; compile against either baseline or candidate classes. */
class ReplayEvaluation {
    record Clip(String path, Category category, float[] audio) { }
    static final Locale LOCALE = Locale.ROOT;
    static long calls, nanos;
    static final List<Long> blockNanos = new ArrayList<>();
    public static void main(String[] args) throws Exception {
        Path training = Path.of(args[0]);
        String split = args[1];
        if (!Set.of("development", "final-test").contains(split)) throw new IllegalArgumentException("Explicit split required");
        var model = new TrainingRepository(training).load().classifier().orElseThrow();
        SoundClassifier measured = features -> {
            long before = System.nanoTime();
            var result = model.classify(features);
            nanos += System.nanoTime() - before; calls++;
            return result;
        };
        var clips = new ArrayList<Clip>();
        for (String row : Files.readAllLines(Path.of("datasets/groups.tsv"))) {
            String[] cells = row.split("\t");
            if (cells[0].startsWith("datasets/" + split + "/") || split.equals("development") && cells[0].startsWith("datasets/held-out/"))
                clips.add(new Clip(cells[0], Category.fromId(cells[1]), WavSource.load(Path.of(cells[0]))));
        }
        var backgrounds = clips.stream().filter(c -> c.category == Category.BACKGROUND).toList();
        System.out.println("kind\tscenario\tfile\ttruth\tstart\tend\temission\ttrigger\tpredicted\tvote\tdistance\talert");
        for (int n = 0; n < clips.size(); n++) {
            Clip clip = clips.get(n);
            var direct = DetectionPipeline.classifyClip(clip.audio, measured, Instant.EPOCH, clip.path);
            print("DIRECT", "direct", clip, 0, clip.audio.length / 16000., 0, direct);
            for (String scenario : List.of("raw", "quiet", "low", "high", "bed")) {
                int prefix = scenario.equals("raw") ? 0 : 48000;
                float[] audio = new float[prefix + clip.audio.length];
                double gain = scenario.equals("low") ? .25 : scenario.equals("high") ? 2 : 1;
                if (scenario.equals("bed")) bed(audio, backgrounds.get(n % backgrounds.size()).audio, .015);
                for (int i = 0; i < clip.audio.length; i++) audio[prefix + i] = (float) Math.clamp(audio[prefix + i] + clip.audio[i] * gain, -1, 1);
                record(scenario, clip, prefix / 16000., audio.length / 16000.);
                var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), measured, Instant.EPOCH, scenario);
                feed(pipeline, audio, scenario, clip, prefix / 16000., audio.length / 16000.);
                pipeline.finish().ifPresent(e -> print("EVENT", scenario, clip, prefix / 16000., audio.length / 16000., pipeline.audioSeconds(), e));
            }
        }
        // One uninterrupted stream. Every file is inserted once, separated by 3 s real background.
        var pipeline = new DetectionPipeline(AdaptiveDetector.Config.defaults(), measured, Instant.EPOCH, "multi");
        for (int n = 0; n < clips.size(); n++) {
            Clip clip = clips.get(n), background = backgrounds.get(n % backgrounds.size());
            float[] gap = new float[48000]; bed(gap, background.audio, .015);
            double start = pipeline.audioSeconds(), end = start + 3;
            record("multi-gap", background, start, end);
            feed(pipeline, gap, "multi-gap", background, start, end);
            start = pipeline.audioSeconds(); end = start + clip.audio.length / 16000.;
            record("multi", clip, start, end);
            feed(pipeline, clip.audio, "multi", clip, start, end);
        }
        Clip last = clips.getLast();
        pipeline.finish().ifPresent(e -> print("EVENT", "multi", last, pipeline.audioSeconds() - last.audio.length / 16000., pipeline.audioSeconds(), pipeline.audioSeconds(), e));
        System.err.printf(LOCALE, "Classifier calls=%d mean kNN=%.3fms (DSP and wall replay timed separately)%n", calls, nanos / 1e6 / calls);
        blockNanos.sort(Long::compare);
        System.err.printf(LOCALE, "accept() DSP+scheduling+kNN: blocks=%d p95=%.3fms max=%.3fms; excludes CSV/UI and EOF%n",
                blockNanos.size(), blockNanos.get((int) (blockNanos.size() * .95)) / 1e6, blockNanos.getLast() / 1e6);
    }
    static void bed(float[] destination, float[] bed, double rms) {
        double gain = rms / Math.max(1e-9, AudioSamples.rms(bed));
        for (int i = 0; i < destination.length; i++) destination[i] = (float) Math.clamp(bed[i % bed.length] * gain, -1, 1);
    }
    static void feed(DetectionPipeline pipeline, float[] audio, String scenario, Clip clip, double start, double end) {
        for (int i = 0; i < audio.length; i += 1600) {
            long before = System.nanoTime();
            var result = pipeline.accept(Arrays.copyOfRange(audio, i, Math.min(audio.length, i + 1600)));
            blockNanos.add(System.nanoTime() - before);
            result.ifPresent(e -> print("EVENT", scenario, clip, start, end, pipeline.audioSeconds(), e));
        }
    }
    static void record(String scenario, Clip clip, double start, double end) {
        System.out.printf(LOCALE, "RECORD\t%s\t%s\t%s\t%.6f\t%.6f\t0\t0\t-\t0\t0\tfalse%n", scenario, clip.path, clip.category.id(), start, end);
    }
    static void print(String kind, String scenario, Clip clip, double start, double end, double emission, SoundEvent event) {
        System.out.printf(LOCALE, "%s\t%s\t%s\t%s\t%.6f\t%.6f\t%.6f\t%.6f\t%s\t%.6f\t%.6f\t%b%n", kind, scenario, clip.path, clip.category.id(), start, end, emission, event.audioSeconds(), event.category().id(), event.voteShare(), event.nearestDistance(), event.isAlert());
    }
}
