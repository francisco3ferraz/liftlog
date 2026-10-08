package io.github.francisco3ferraz.liftlog.progression.core;

import io.github.francisco3ferraz.liftlog.progression.core.SetPairing.Mark;
import java.util.Objects;

/** Tag of one exercise in one session, derived from its set pairing. Neutral marks are ignored. */
public enum ExerciseTag {
    PROGRESSED,
    MATCHED,
    MIXED,
    REGRESSED,
    FIRST;

    /** Fewer working sets than prescribed can never be {@link #PROGRESSED}; it falls back to {@link #MATCHED}. */
    public static ExerciseTag of(SetPairing pairing, int prescribedSets) {
        Objects.requireNonNull(pairing, "pairing");
        if (pairing.pairs().stream().allMatch(pair -> pair.baseline().isEmpty())) {
            return FIRST;
        }
        var marks = pairing.marks();
        boolean up = marks.contains(Mark.UP);
        boolean down = marks.contains(Mark.DOWN);
        if (up && down) {
            return MIXED;
        }
        if (down) {
            return REGRESSED;
        }
        if (up && !pairing.fewerThanPrescribed(prescribedSets)) {
            return PROGRESSED;
        }
        return MATCHED;
    }
}
