package io.github.francisco3ferraz.liftlog.progression.core;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One logged set as the progression engine sees it. {@code completedReps} never includes a failed rep, and a failed set
 * carries no RIR.
 */
public record LoggedSet(Kind kind, BigDecimal weightKg, int completedReps, boolean failed, Optional<Integer> rir) {

    private static final BigDecimal WEIGHT_STEP_KG = new BigDecimal("0.25");
    private static final int MAX_RIR = 4;

    public enum Kind {
        WORKING,
        WARMUP
    }

    public LoggedSet {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(weightKg, "weightKg");
        Objects.requireNonNull(rir, "rir");
        if (weightKg.signum() < 0) {
            throw new IllegalArgumentException("weightKg must be >= 0, was " + weightKg);
        }
        if (weightKg.remainder(WEIGHT_STEP_KG).signum() != 0) {
            throw new IllegalArgumentException("weightKg must be a multiple of 0.25, was " + weightKg);
        }
        if (completedReps < 0) {
            throw new IllegalArgumentException("completedReps must be >= 0, was " + completedReps);
        }
        if (failed && rir.isPresent()) {
            throw new IllegalArgumentException("rir must be empty on a failed set, was " + rir.get());
        }
        if (rir.isPresent() && (rir.get() < 0 || rir.get() > MAX_RIR)) {
            throw new IllegalArgumentException("rir must be between 0 and " + MAX_RIR + ", was " + rir.get());
        }
    }

    public boolean isWorking() {
        return kind == Kind.WORKING;
    }
}
