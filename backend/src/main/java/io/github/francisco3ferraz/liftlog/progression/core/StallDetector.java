package io.github.francisco3ferraz.liftlog.progression.core;

import java.util.List;
import java.util.Objects;

/**
 * Stall flag of a progression line. A session progressed when at least one set is {@link SetPairing.Mark#PROGRESSED}
 * and none is {@link SetPairing.Mark#REGRESSED}. A session with no baseline starts a new run.
 */
public final class StallDetector {

    private StallDetector() {}

    /** {@code recentPairings} is ordered oldest first, one pairing per session of the line. */
    public static boolean isStalled(List<SetPairing> recentPairings, int threshold) {
        Objects.requireNonNull(recentPairings, "recentPairings");
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be >= 1, was " + threshold);
        }
        int run = 0;
        for (var pairing : recentPairings.reversed()) {
            if (!hasBaseline(pairing) || progressed(pairing)) {
                break;
            }
            if (++run == threshold) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBaseline(SetPairing pairing) {
        return pairing.pairs().stream().anyMatch(pair -> pair.baseline().isPresent());
    }

    private static boolean progressed(SetPairing pairing) {
        var marks = pairing.marks();
        return marks.contains(SetPairing.Mark.PROGRESSED) && !marks.contains(SetPairing.Mark.REGRESSED);
    }
}
