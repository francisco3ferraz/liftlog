package io.github.francisco3ferraz.liftlog.progression.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** What to aim for next session in a progression line: one target per prescribed set, or none without a baseline. */
public record Recommendation(Decision decision, List<Target> targets) {

    public enum Decision {
        INCREASE,
        HOLD,
        NO_BASELINE
    }

    /** Target weight and reps for one set. The weight is kept at two decimals so equal loads compare equal. */
    public record Target(BigDecimal weightKg, int reps) {

        public Target {
            weightKg = Objects.requireNonNull(weightKg, "weightKg").setScale(2);
        }
    }

    public Recommendation {
        Objects.requireNonNull(decision, "decision");
        targets = List.copyOf(targets);
    }

    static Recommendation noBaseline() {
        return new Recommendation(Decision.NO_BASELINE, List.of());
    }

    static Recommendation increase(List<LoggedSet> prescribed, BigDecimal incrementKg, int repMin) {
        var targets = prescribed.stream()
                .map(set -> new Target(set.weightKg().add(incrementKg), repMin))
                .toList();
        return new Recommendation(Decision.INCREASE, targets);
    }

    static Recommendation hold(List<LoggedSet> prescribed, int repMin, int repMax) {
        var targets = prescribed.stream()
                .map(set -> new Target(set.weightKg(), Math.max(Math.min(set.completedReps() + 1, repMax), repMin)))
                .toList();
        return new Recommendation(Decision.HOLD, targets);
    }
}
