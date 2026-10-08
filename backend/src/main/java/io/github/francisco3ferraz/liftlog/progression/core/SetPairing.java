package io.github.francisco3ferraz.liftlog.progression.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Today's working sets lined up against the baseline's by position. Warm-ups are dropped from both sides. A set with no
 * partner on the other side is marked {@link Mark#EXTRA} or {@link Mark#MISSING}.
 */
public record SetPairing(List<Pair> pairs, int todayWorkingSets) {

    public enum Mark {
        PROGRESSED,
        MATCHED,
        REGRESSED,
        EXTRA,
        MISSING;

        /** Neutral marks count neither for nor against the exercise. */
        public boolean isNeutral() {
            return this == EXTRA || this == MISSING;
        }

        static Mark of(SetComparison comparison) {
            return switch (comparison) {
                case PROGRESSED -> PROGRESSED;
                case MATCHED -> MATCHED;
                case REGRESSED -> REGRESSED;
            };
        }
    }

    /** One position. At least one of {@code today} and {@code baseline} is present. */
    public record Pair(Optional<LoggedSet> today, Optional<LoggedSet> baseline, Mark mark) {}

    public SetPairing {
        pairs = List.copyOf(pairs);
    }

    /** Both lists are ordered by position. */
    public static SetPairing pair(List<LoggedSet> today, List<LoggedSet> baseline, int repMin) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(baseline, "baseline");
        var todayWorking = today.stream().filter(LoggedSet::isWorking).toList();
        var baselineWorking = baseline.stream().filter(LoggedSet::isWorking).toList();
        var pairs = new ArrayList<Pair>();
        for (int i = 0; i < Math.max(todayWorking.size(), baselineWorking.size()); i++) {
            if (i >= baselineWorking.size()) {
                pairs.add(new Pair(Optional.of(todayWorking.get(i)), Optional.empty(), Mark.EXTRA));
            } else if (i >= todayWorking.size()) {
                pairs.add(new Pair(Optional.empty(), Optional.of(baselineWorking.get(i)), Mark.MISSING));
            } else {
                var mark = Mark.of(SetComparison.compare(todayWorking.get(i), baselineWorking.get(i), repMin));
                pairs.add(new Pair(Optional.of(todayWorking.get(i)), Optional.of(baselineWorking.get(i)), mark));
            }
        }
        return new SetPairing(pairs, todayWorking.size());
    }

    public List<Mark> marks() {
        return pairs.stream().map(Pair::mark).toList();
    }

    public boolean fewerThanPrescribed(int prescribedSets) {
        return todayWorkingSets < prescribedSets;
    }
}
