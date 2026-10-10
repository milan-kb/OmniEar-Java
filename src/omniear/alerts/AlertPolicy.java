package omniear.alerts;

import java.time.Instant;
import java.util.UUID;
import omniear.detection.AdaptiveDetector;
import omniear.model.*;

/** Original alert routing and gates, explicitly applied to uncalibrated k-NN vote shares. */
public final class AlertPolicy {
    public SoundEvent event(Instant timestamp, String source, double audioSeconds,
            AdaptiveDetector.Reading reading, SoundClassifier.Prediction prediction) {
        Category category = prediction == null ? null : prediction.category();
        double votes = prediction == null ? 0 : prediction.voteShare();
        SoundEvent.Status status = prediction == null ? SoundEvent.Status.UNTRAINED
                : category == Category.BACKGROUND ? SoundEvent.Status.BACKGROUND_SUPPRESSED
                : votes < category.alertGate() ? SoundEvent.Status.LOW_VOTE_SUPPRESSED : SoundEvent.Status.ALERT;
        Priority priority = status == SoundEvent.Status.ALERT ? category.priority() : Priority.NONE;
        return new SoundEvent(UUID.randomUUID(), timestamp, source, audioSeconds, reading.rms(), reading.baseline(),
                reading.threshold(), category, votes, prediction == null ? 0 : prediction.nearestDistance(), priority, status);
    }
}
