import java.nio.file.*;
import java.util.*;
import omniear.audio.*;
import omniear.model.*;

/** Development-only window predictions. Does not read final-test. No synthetic training. */
class WindowDevelopment {
    static FeatureVector features(float[] audio, int window, boolean normalize) {
        return new FeatureExtractor(window, normalize).extract(audio);
    }
    public static void main(String[] args) throws Exception {
        int window = Integer.parseInt(args[0]);
        boolean normalize = Boolean.parseBoolean(args[1]);
        boolean original = args.length > 2 && args[2].equals("original");
        var examples = new ArrayList<TrainingExample>();
        Set<String> originalIds = original ? new HashSet<>(Files.readAllLines(Path.of("data/downloads/original-training.txt"))) : Set.of();
        var development = new ArrayList<String[]>();
        for (String line : Files.readAllLines(Path.of("datasets/groups.tsv"))) {
            String[] row = line.split("\t");
            if (row[0].startsWith("datasets/training/") && (!original || originalIds.contains(row[0])))
                examples.add(new TrainingExample(Category.fromId(row[1]), features(WavSource.load(Path.of(row[0])), window, normalize), row[0]));
            else if (row[0].startsWith("datasets/held-out/") || row[0].startsWith("datasets/development/")) development.add(row);
        }
        var model = new KnnClassifier(examples, 7);
        System.out.println("scenario\tfile\ttruth\ttime\tonset\tcategory\tvote\tdistance\trms");
        for (String[] row : development) {
            float[] originalAudio = WavSource.load(Path.of(row[0]));
            for (String scenario : List.of("raw", "quiet", "low", "high", "bed")) {
                int prelude = scenario.equals("raw") ? 0 : 48000;
                float[] audio = new float[prelude + originalAudio.length];
                if (scenario.equals("bed")) {
                    var backgrounds = development.stream().filter(r -> r[1].equals("background")).toList();
                    float[] bed = WavSource.load(Path.of(backgrounds.get(development.indexOf(row) % backgrounds.size())[0]));
                    double bedGain = .015 / Math.max(1e-9, AudioSamples.rms(bed));
                    for (int i = 0; i < audio.length; i++) audio[i] = (float) Math.clamp(bed[i % bed.length] * bedGain, -1, 1);
                }
                double gain = scenario.equals("low") ? .25 : scenario.equals("high") ? 2 : 1;
                for (int i = 0; i < originalAudio.length; i++) audio[prelude + i] = (float) Math.clamp(audio[prelude + i] + originalAudio[i] * gain, -1, 1);
                double previous = 0;
                int onset = -160000, last = 0;
                for (int end = Math.min(1600, audio.length); ; end = Math.min(end + 1600, audio.length)) {
                    double rms = AudioSamples.rms(Arrays.copyOfRange(audio, last, end));
                    if (rms > .002 && rms > previous * 2) onset = end;
                    previous = .8 * previous + .2 * rms;
                    last = end;
                    if ((end >= 16000 && end % 8000 == 0) || end == audio.length) {
                        float[] clip = Arrays.copyOfRange(audio, Math.max(0, end - window), end);
                        double energy = AudioSamples.rms(clip);
                        if (energy > .0001) {
                            var p = model.classify(features(clip, window, normalize));
                            System.out.printf(Locale.ROOT, "%s\t%s\t%s\t%.4f\t%b\t%s\t%.6f\t%.6f\t%.6f%n", scenario, row[0], row[1], end / 16000., end - onset <= window, p.category().id(), p.voteShare(), p.nearestDistance(), energy);
                        }
                    }
                    if (end == audio.length) break;
                }
            }
        }
    }
}
