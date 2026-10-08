package io.github.francisco3ferraz.liftlog.progression.core;

import java.util.List;
import java.util.Objects;

/** Double-progression decision for one progression line. Pure: no I/O, no Spring. */
public final class DoubleProgression {

    private DoubleProgression() {}

    /** {@code baseline} is ordered by position. Only the first {@code prescribedSets} working sets count. */
    public static Recommendation recommend(LineSettings line, List<LoggedSet> baseline) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(baseline, "baseline");
        var working = baseline.stream().filter(LoggedSet::isWorking).toList();
        if (working.isEmpty()) {
            return Recommendation.noBaseline();
        }
        var prescribed = working.subList(0, Math.min(working.size(), line.prescribedSets()));
        boolean allAtTop = prescribed.size() == line.prescribedSets()
                && prescribed.stream().allMatch(s -> s.completedReps() >= line.repMax());
        boolean rirOk = !line.rirGate()
                || prescribed.stream()
                        .allMatch(
                                s -> s.rir().map(r -> r >= line.targetRirMin()).orElse(true));

        return allAtTop && rirOk
                ? Recommendation.increase(prescribed, line.incrementKg(), line.repMin())
                : Recommendation.hold(prescribed, line.prescribedSets(), line.repMin(), line.repMax());
    }
}
