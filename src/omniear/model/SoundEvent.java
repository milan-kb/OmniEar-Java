package omniear.model;

import java.time.Instant;
import java.util.*;
import omniear.alerts.Priority;

/** Metadata only: no raw audio is persisted in events. Null category means unclassified. */
public record SoundEvent(UUID id, Instant timestamp, String source, double audioSeconds,
        double rms, double baseline, double threshold, Category category, double voteShare,
        double nearestDistance, Priority priority, Status status) {
    public enum Status { ALERT, BACKGROUND_SUPPRESSED, LOW_VOTE_SUPPRESSED, UNTRAINED,
        DUPLICATE_SUPPRESSED, DISTANCE_SUPPRESSED, BACKGROUND_CONFLICT_SUPPRESSED }
    public SoundEvent {
        Objects.requireNonNull(id); Objects.requireNonNull(timestamp); Objects.requireNonNull(source);
        Objects.requireNonNull(priority); Objects.requireNonNull(status);
        if (source.length() > 8192) throw new IllegalArgumentException("Source name too long");
        for (double value : new double[]{audioSeconds, rms, baseline, threshold, voteShare, nearestDistance}) {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid event number");
        }
        if (voteShare > 1 || (status == Status.UNTRAINED) != (category == null)
                || (status == Status.ALERT && (category == null || category == Category.BACKGROUND || priority != category.priority()))
                || (status != Status.ALERT && priority != Priority.NONE)
                || (status == Status.BACKGROUND_SUPPRESSED && category != Category.BACKGROUND)
                || (status == Status.LOW_VOTE_SUPPRESSED && (category == Category.BACKGROUND || voteShare >= category.alertGate()))
                || ((status == Status.DUPLICATE_SUPPRESSED || status == Status.DISTANCE_SUPPRESSED
                    || status == Status.BACKGROUND_CONFLICT_SUPPRESSED) && (category == null || category == Category.BACKGROUND))) {
            throw new IllegalArgumentException("Inconsistent event category/status/priority");
        }
    }
    public boolean isAlert() { return status == Status.ALERT; }
}
