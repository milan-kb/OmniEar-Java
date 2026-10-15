package omniear.alerts;

import java.time.Instant;
import java.util.UUID;
import omniear.detection.AdaptiveDetector;
import omniear.model.*;

/** Selective operating points measured on source-disjoint development, not probabilities. */
public final class AlertPolicy {
    // Calibration protocol/results: evaluation/ACOUSTIC_DECISION.md. Unchanged
    // votes remain in CSV as diagnostics; a high vote alone cannot route an alert.
    private static final double[] RADIUS = {1.5, .3, 1, .75};
    private static final double[] BACKGROUND_RATIO = {.8, .7, .8, .7};

    public SoundEvent event(Instant timestamp, String source, double audioSeconds,
            AdaptiveDetector.Reading reading, SoundClassifier.Prediction prediction) {
        Category category = prediction == null ? null : prediction.category();
        double votes = prediction == null ? 0 : prediction.voteShare();
        SoundEvent.Status status = prediction == null ? SoundEvent.Status.UNTRAINED
                : category == Category.BACKGROUND ? SoundEvent.Status.BACKGROUND_SUPPRESSED
                : prediction.categoryDistance() > RADIUS[category.ordinal()] ? SoundEvent.Status.DISTANCE_SUPPRESSED
                : prediction.backgroundRatio() > BACKGROUND_RATIO[category.ordinal()] ? SoundEvent.Status.BACKGROUND_CONFLICT_SUPPRESSED
                : SoundEvent.Status.ALERT;
        Priority priority = status == SoundEvent.Status.ALERT ? category.priority() : Priority.NONE;
        return new SoundEvent(UUID.randomUUID(), timestamp, source, audioSeconds, reading.rms(), reading.baseline(),
                reading.threshold(), category, votes, prediction == null ? 0 : prediction.nearestDistance(), priority, status);
    }
}
