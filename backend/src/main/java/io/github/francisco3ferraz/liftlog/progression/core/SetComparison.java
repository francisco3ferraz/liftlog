package io.github.francisco3ferraz.liftlog.progression.core;

import java.util.Objects;

/** Tag of today's working set against the same set in the baseline session. RIR plays no part. */
public enum SetComparison {
    PROGRESSED,
    MATCHED,
    REGRESSED;

    /** A higher weight only counts as {@link #PROGRESSED} when today's set still reaches {@code repMin}. */
    public static SetComparison compare(LoggedSet today, LoggedSet baseline, int repMin) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(baseline, "baseline");
        int weight = today.weightKg().compareTo(baseline.weightKg());
        int reps = Integer.compare(today.completedReps(), baseline.completedReps());
        if (weight > 0) {
            return today.completedReps() >= repMin ? PROGRESSED : REGRESSED;
        }
        if (weight == 0 && reps > 0) {
            return PROGRESSED;
        }
        if (weight == 0 && reps == 0) {
            return MATCHED;
        }
        return REGRESSED;
    }
}
