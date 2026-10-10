import java.nio.file.*;
import java.util.*;
import omniear.audio.WavSource;
import omniear.model.*;

/** Leave-source-group-out development only: never reads held-out audio. */
public class DevelopmentCheck {
    public static void main(String[] args) throws Exception {
        var examples = new ArrayList<TrainingExample>();
        var groups = new ArrayList<String>();
        var extractor = new FeatureExtractor();
        for (String line : Files.readAllLines(Path.of("datasets/groups.tsv"))) {
            String[] fields = line.split("\t");
            if (!fields[0].startsWith("datasets/training/")) continue;
            var category = Arrays.stream(Category.values()).filter(c -> c.id().equals(fields[1])).findFirst().orElseThrow();
            examples.add(new TrainingExample(category, extractor.extract(WavSource.load(Path.of(fields[0]))), fields[0]));
            groups.add(fields[2]);
        }
        System.out.println("Training-only leave-source-group-out; no test files, no threshold tuning.");
        for (boolean balanced : new boolean[]{false, true}) for (int k : new int[]{1, 3, 5, 7}) {
            int[][] matrix = new int[5][5];
            int skipped = 0;
            for (String group : new TreeSet<>(groups)) {
                var train = new ArrayList<TrainingExample>();
                for (int i = 0; i < examples.size(); i++) if (!groups.get(i).equals(group)) train.add(examples.get(i));
                if (train.stream().map(TrainingExample::category).distinct().count() < 5) {
                    skipped += Collections.frequency(groups, group); continue;
                }
                var model = new KnnClassifier(train, k);
                for (int i = 0; i < examples.size(); i++) if (groups.get(i).equals(group)) {
                    var example = examples.get(i);
                    var prediction = experimentalPrediction(train, example.features(), k, balanced);
                    if (balanced && prediction != model.classify(example.features()).category()) throw new AssertionError("Runtime/experiment disagreement");
                    matrix[example.category().ordinal()][prediction.ordinal()]++;
                }
            }
            int correct = 0, total = 0;
            double macroRecall = 0;
            for (Category c : Category.values()) {
                int i = c.ordinal(), n = Arrays.stream(matrix[i]).sum();
                correct += matrix[i][i]; total += n;
                macroRecall += n == 0 ? 0 : matrix[i][i] / (double) n / 5;
                System.out.println("k=" + k + " " + c.id() + " " + Arrays.toString(matrix[i]));
            }
            System.out.printf(Locale.ROOT, "balanced=%s k=%d accuracy=%.2f%% macro-recall=%.2f%% n=%d skipped=%d%n", balanced, k, 100.0 * correct / total, 100 * macroRecall, total, skipped);
        }
    }

    // Development experiment: inverse class-frequency votes, training-only scales.
    private static Category experimentalPrediction(List<TrainingExample> train, FeatureVector query, int k, boolean balanced) {
        double[] mean = new double[60], scale = new double[60], votes = new double[5];
        int[] counts = new int[5];
        for (var e : train) {
            counts[e.category().ordinal()]++;
            for (int i = 0; i < 60; i++) mean[i] += e.features().get(i) / train.size();
        }
        for (var e : train) for (int i = 0; i < 60; i++) scale[i] += Math.pow(e.features().get(i) - mean[i], 2) / train.size();
        for (int i = 0; i < 60; i++) scale[i] = scale[i] < 1e-12 ? 1 : Math.sqrt(scale[i]);
        record Neighbor(Category category, double distance) { }
        var nearest = new ArrayList<Neighbor>();
        for (var e : train) {
            double distance = 0;
            for (int i = 0; i < 60; i++) distance += Math.pow((query.get(i) - e.features().get(i)) / scale[i], 2);
            nearest.add(new Neighbor(e.category(), Math.sqrt(distance / 60)));
        }
        nearest.sort(Comparator.comparingDouble(Neighbor::distance));
        for (var n : nearest.subList(0, Math.min(k, nearest.size()))) {
            votes[n.category().ordinal()] += 1 / Math.max(1e-9, n.distance()) / (balanced ? counts[n.category().ordinal()] : 1);
        }
        int best = 4;
        for (int i = 0; i < 5; i++) if (votes[i] > votes[best]) best = i;
        return Category.values()[best];
    }
}
