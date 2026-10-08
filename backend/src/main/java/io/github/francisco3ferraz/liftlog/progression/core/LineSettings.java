package io.github.francisco3ferraz.liftlog.progression.core;

import java.math.BigDecimal;
import java.util.Objects;

/** Settings of one progression line. {@code rirGate} makes the increase rule also require the target RIR. */
public record LineSettings(
        int repMin,
        int repMax,
        BigDecimal incrementKg,
        int targetRirMin,
        int targetRirMax,
        int prescribedSets,
        boolean rirGate) {

    public LineSettings {
        Objects.requireNonNull(incrementKg, "incrementKg");
        if (repMin < 1) {
            throw new IllegalArgumentException("repMin must be >= 1, was " + repMin);
        }
        if (repMax < repMin) {
            throw new IllegalArgumentException("repMax must be >= repMin (" + repMin + "), was " + repMax);
        }
        if (incrementKg.signum() <= 0) {
            throw new IllegalArgumentException("incrementKg must be > 0, was " + incrementKg);
        }
        if (targetRirMax < targetRirMin) {
            throw new IllegalArgumentException(
                    "targetRirMax must be >= targetRirMin (" + targetRirMin + "), was " + targetRirMax);
        }
        if (prescribedSets < 1) {
            throw new IllegalArgumentException("prescribedSets must be >= 1, was " + prescribedSets);
        }
    }
}
