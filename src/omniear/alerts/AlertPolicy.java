package omniear.alerts;

import java.time.Instant;
import java.util.UUID;
import omniear.detection.AdaptiveDetector;
import omniear.model.*;

/** Original priorities/minimum votes plus development-selected neighbor support gates. */
public final class AlertPolicy {
    public static boolean supported(SoundClassifier.Prediction prediction) {
        Category category = prediction.category();
        double vote = switch (category) {
            case SCREAM_DISTRESS, IMPACT_CRASH -> .7;
            case EXPLOSION -> .99;
            default -> category.alertGate();
        };
        double distance = switch (category) {
            case SCREAM_DISTRESS, IMPACT_CRASH -> .8;
            case EXPLOSION -> .3;
            default -> .7;
        };
        return category != Category.BACKGROUND && prediction.voteShare() >= vote && prediction.nearestDistance() <= distance;
    }

    public SoundEvent event(Instant timestamp, String source, double audioSeconds,
            AdaptiveDetector.Reading reading, SoundClassifier.Prediction prediction) {
        Category category = prediction == null ? null : prediction.category();
        double votes = prediction == null ? 0 : prediction.voteShare();
        SoundEvent.Status status = prediction == null ? SoundEvent.Status.UNTRAINED
                : category == Category.BACKGROUND ? SoundEvent.Status.BACKGROUND_SUPPRESSED
                : votes < category.alertGate() ? SoundEvent.Status.LOW_VOTE_SUPPRESSED
                : !supported(prediction) ? SoundEvent.Status.EVIDENCE_SUPPRESSED : SoundEvent.Status.ALERT;
        Priority priority = status == SoundEvent.Status.ALERT ? category.priority() : Priority.NONE;
        return new SoundEvent(UUID.randomUUID(), timestamp, source, audioSeconds, reading.rms(), reading.baseline(),
                reading.threshold(), category, votes, prediction == null ? 0 : prediction.nearestDistance(), priority, status);
    }
}
