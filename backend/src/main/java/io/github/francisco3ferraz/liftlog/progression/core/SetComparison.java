package io.github.francisco3ferraz.liftlog.progression.core;

import java.util.Objects;

/** Outcome of comparing today's working set with its baseline partner. RIR plays no part. */
public enum SetComparison {
    UP,
    EQUAL,
    DOWN;

    /** A higher weight only counts as {@link #UP} when today's set still reaches {@code repMin}. */
    public static SetComparison compare(LoggedSet today, LoggedSet baseline, int repMin) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(baseline, "baseline");
        int weight = today.weightKg().compareTo(baseline.weightKg());
        int reps = Integer.compare(today.completedReps(), baseline.completedReps());
        if (weight > 0) {
            return today.completedReps() >= repMin ? UP : DOWN;
        }
        if (weight == 0 && reps > 0) {
            return UP;
        }
        if (weight == 0 && reps == 0) {
            return EQUAL;
        }
        return DOWN;
    }
}
