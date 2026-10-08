package io.github.francisco3ferraz.liftlog.progression.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.progression.core.LoggedSet.Kind;
import io.github.francisco3ferraz.liftlog.progression.core.Recommendation.Decision;
import io.github.francisco3ferraz.liftlog.progression.core.Recommendation.Target;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.IntRange;

class DoubleProgressionProperties {

    /** A line and a baseline of at least {@code prescribedSets} working sets, with warm-ups mixed in. */
    record Scenario(LineSettings line, List<LoggedSet> baseline) {}

    @Property
    void aFailedRepNeverTriggersAnIncrease(
            @ForAll("fullBaselines") Scenario scenario, @ForAll @IntRange(min = 0, max = 99) int position) {
        var line = scenario.line();
        var baseline = new ArrayList<>(workingOnly(scenario.baseline()));
        int index = position % line.prescribedSets();
        var original = baseline.get(index);
        // The failed rep was within the range, so completedReps stops short of repMax.
        baseline.set(index, failedSet(original.weightKg(), Math.min(original.completedReps(), line.repMax() - 1)));

        var recommendation = DoubleProgression.recommend(line, baseline);

        assertThat(recommendation.decision()).isNotEqualTo(Decision.INCREASE);
    }

    @Property
    void anIncreaseAlwaysResetsTheTargetToRepMin(@ForAll("fullBaselines") Scenario scenario) {
        var line = scenario.line();
        var prescribed = workingOnly(scenario.baseline()).subList(0, line.prescribedSets());

        var recommendation = DoubleProgression.recommend(line, scenario.baseline());

        if (recommendation.decision() == Decision.INCREASE) {
            var expected = prescribed.stream()
                    .map(set -> new Target(set.weightKg().add(line.incrementKg()), line.repMin()))
                    .toList();
            assertThat(recommendation.targets()).containsExactlyElementsOf(expected);
        }
    }

    @Property
    void extraWorkingSetsBeyondPrescribedSetsNeverBlockAnIncrease(
            @ForAll("increasingBaselines") Scenario scenario, @ForAll("extraSets") List<LoggedSet> extras) {
        var extended = new ArrayList<>(scenario.baseline());
        extended.addAll(extras);

        var withoutExtras = DoubleProgression.recommend(scenario.line(), scenario.baseline());
        var withExtras = DoubleProgression.recommend(scenario.line(), extended);

        assertThat(withoutExtras.decision()).isEqualTo(Decision.INCREASE);
        assertThat(withExtras).isEqualTo(withoutExtras);
    }

    @Property
    void aBaselineWithFewerThanPrescribedWorkingSetsNeverIncreases(@ForAll("shortBaselines") Scenario scenario) {
        var recommendation = DoubleProgression.recommend(scenario.line(), scenario.baseline());

        assertThat(recommendation.decision()).isNotEqualTo(Decision.INCREASE);
    }

    @Property
    void sameWeightWithMoreRepsIsAlwaysProgressed(
            @ForAll("sets") LoggedSet baseline,
            @ForAll @IntRange(min = 1, max = 30) int moreReps,
            @ForAll @IntRange(min = 1, max = 30) int repMin) {
        var today = workingSet(baseline.weightKg(), baseline.completedReps() + moreReps, Optional.empty());

        assertThat(SetComparison.compare(today, baseline, repMin)).isEqualTo(SetComparison.PROGRESSED);
    }

    @Property
    void targetsNeverExceedRepMax(@ForAll("anyBaselines") Scenario scenario) {
        var recommendation = DoubleProgression.recommend(scenario.line(), scenario.baseline());

        assertThat(recommendation.targets())
                .allSatisfy(target -> assertThat(target.reps())
                        .isLessThanOrEqualTo(scenario.line().repMax()));
    }

    @Provide
    Arbitrary<Scenario> fullBaselines() {
        return lines().flatMap(line -> Arbitraries.integers()
                .between(line.prescribedSets(), line.prescribedSets() + 3)
                .flatMap(count -> baseline(line, count)));
    }

    @Provide
    Arbitrary<Scenario> shortBaselines() {
        return lines().filter(line -> line.prescribedSets() > 1)
                .flatMap(line -> Arbitraries.integers()
                        .between(0, line.prescribedSets() - 1)
                        .flatMap(count -> baseline(line, count)));
    }

    @Provide
    Arbitrary<Scenario> anyBaselines() {
        return Arbitraries.oneOf(fullBaselines(), shortBaselines());
    }

    @Provide
    Arbitrary<Scenario> increasingBaselines() {
        return lines().flatMap(line -> {
            var topSet = Combinators.combine(
                            weights(),
                            Arbitraries.integers().between(line.repMax(), line.repMax() + 3),
                            Arbitraries.integers()
                                    .between(line.targetRirMin(), 4)
                                    .optional())
                    .as(DoubleProgressionProperties::workingSet);
            return topSet.list().ofSize(line.prescribedSets()).map(sets -> new Scenario(line, sets));
        });
    }

    @Provide
    Arbitrary<List<LoggedSet>> extraSets() {
        return sets().list().ofMinSize(1).ofMaxSize(4);
    }

    @Provide
    Arbitrary<LoggedSet> sets() {
        var working = Combinators.combine(
                        weights(),
                        Arbitraries.integers().between(0, 25),
                        Arbitraries.integers().between(0, 4).optional())
                .as(DoubleProgressionProperties::workingSet);
        var failed = Combinators.combine(weights(), Arbitraries.integers().between(0, 25))
                .as(DoubleProgressionProperties::failedSet);
        return Arbitraries.frequencyOf(Tuple.of(4, working), Tuple.of(1, failed));
    }

    private static Arbitrary<LineSettings> lines() {
        return Combinators.combine(
                        Arbitraries.integers().between(1, 20),
                        Arbitraries.integers().between(0, 10),
                        Arbitraries.integers().between(1, 40),
                        Arbitraries.integers().between(0, 4),
                        Arbitraries.integers().between(1, 6),
                        Arbitraries.of(true, false))
                .as((repMin, span, incrementQuarters, rirMin, prescribedSets, rirGate) -> new LineSettings(
                        repMin, repMin + span, quarters(incrementQuarters), rirMin, 4, prescribedSets, rirGate));
    }

    /** {@code workingCount} working sets near the top of the range, so increases are common, plus warm-ups. */
    private static Arbitrary<Scenario> baseline(LineSettings line, int workingCount) {
        var reps = Arbitraries.frequencyOf(
                Tuple.of(3, Arbitraries.integers().between(line.repMax(), line.repMax() + 2)),
                Tuple.of(1, Arbitraries.integers().between(0, line.repMax())));
        var working = Combinators.combine(
                        weights(), reps, Arbitraries.integers().between(0, 4).optional())
                .as(DoubleProgressionProperties::workingSet);
        var warmup = Combinators.combine(weights(), Arbitraries.integers().between(1, 15))
                .as((weight, completed) -> new LoggedSet(Kind.WARMUP, weight, completed, false, Optional.empty()));
        return Combinators.combine(
                        working.list().ofSize(workingCount), warmup.list().ofMaxSize(3), Arbitraries.randoms())
                .as((workingSets, warmups, random) -> {
                    var sets = new ArrayList<>(workingSets);
                    warmups.forEach(w -> sets.add(random.nextInt(sets.size() + 1), w));
                    return new Scenario(line, sets);
                });
    }

    private static Arbitrary<BigDecimal> weights() {
        return Arbitraries.integers().between(0, 1000).map(DoubleProgressionProperties::quarters);
    }

    private static BigDecimal quarters(int count) {
        return new BigDecimal("0.25").multiply(BigDecimal.valueOf(count));
    }

    private static List<LoggedSet> workingOnly(List<LoggedSet> sets) {
        return sets.stream().filter(LoggedSet::isWorking).toList();
    }

    private static LoggedSet workingSet(BigDecimal weightKg, int completedReps, Optional<Integer> rir) {
        return new LoggedSet(Kind.WORKING, weightKg, completedReps, false, rir);
    }

    private static LoggedSet failedSet(BigDecimal weightKg, int completedReps) {
        return new LoggedSet(Kind.WORKING, weightKg, completedReps, true, Optional.empty());
    }
}
