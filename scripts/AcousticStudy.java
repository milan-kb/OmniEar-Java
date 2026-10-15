import java.nio.file.*;
import java.util.Locale;
import omniear.model.*;

/** Development probe: retain votes plus absolute/relative neighbor geometry. No final inference. */
class AcousticStudy {
    public static void main(String[] args) throws Exception {
        var model = new TrainingRepository(Path.of(args[0])).load().classifier().orElseThrow();
        ReplayEvaluation.extraHeader = "\tcategory_distance\tbackground_ratio";
        ReplayEvaluation.suppliedModel = features -> {
            var p = model.classify(features);
            ReplayEvaluation.extraFields = String.format(Locale.ROOT, "\t%.9f\t%.9f", p.categoryDistance(), p.backgroundRatio());
            return p;
        };
        ReplayEvaluation.main(new String[]{args[0], "rejection-dev"});
    }
}
