import java.nio.file.*;
import java.util.*;
import omniear.audio.WavSource;
import omniear.model.*;

/** Fixed development-only ablation: original training plus 42 existing real hard negatives. */
class HardNegativeStudy {
    public static void main(String[] args) throws Exception {
        var examples = new ArrayList<TrainingExample>();
        var extractor = new FeatureExtractor();
        for (String row : Files.readAllLines(Path.of("datasets/groups.tsv"))) {
            String[] cells = row.split("\t");
            if (cells[0].startsWith("datasets/training/") || cells[0].startsWith("datasets/additional-training/background/"))
                examples.add(new TrainingExample(Category.fromId(cells[1]), extractor.extract(WavSource.load(Path.of(cells[0]))), cells[0]));
        }
        if (examples.size() != 212) throw new IllegalStateException("Expected frozen 170 + 42 recordings");
        ReplayEvaluation.suppliedModel = new KnnClassifier(examples, 7);
        ReplayEvaluation.main(new String[]{"datasets/training", "rejection-dev"});
    }
}
