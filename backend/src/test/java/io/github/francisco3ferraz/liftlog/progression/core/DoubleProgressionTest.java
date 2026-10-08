package io.github.francisco3ferraz.liftlog.progression.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.progression.core.LoggedSet.Kind;
import io.github.francisco3ferraz.liftlog.progression.core.Recommendation.Target;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DoubleProgressionTest {

    private static final LineSettings SIX_TO_TEN = line(6, 10, 3, false);

    @Test
    void holdsWhenTheLastSetFailedAtTheTop() {
        var baseline = List.of(working("80", 10), working("80", 10), failed("80", 9));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.HOLD);
        assertThat(recommendation.targets()).containsExactly(target("80", 10), target("80", 10), target("80", 10));
    }

    @Test
    void aFailedSetHoldsOneRepAboveItsCompletedReps() {
        var baseline = List.of(working("80", 10), working("80", 10), failed("80", 7));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.HOLD);
        assertThat(recommendation.targets()).containsExactly(target("80", 10), target("80", 10), target("80", 8));
    }

    @Test
    void increasesWhenEveryPrescribedSetReachesTheTop() {
        var baseline = List.of(working("80", 10), working("80", 10), working("80", 10));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.INCREASE);
        assertThat(recommendation.targets()).containsExactly(target("82.5", 6), target("82.5", 6), target("82.5", 6));
    }

    @Test
    void holdsOneRepAboveTheBaselineCappedAtTheTop() {
        var baseline = List.of(working("80", 10), working("80", 8), working("80", 7));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.targets()).containsExactly(target("80", 10), target("80", 9), target("80", 8));
    }

    @Test
    void targetsTheBottomOfTheRangeWhenBelowItAfterAnIncrease() {
        var baseline = List.of(working("82.5", 6), working("82.5", 5), working("82.5", 4));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.HOLD);
        assertThat(recommendation.targets()).containsExactly(target("82.5", 7), target("82.5", 6), target("82.5", 6));
    }

    @Test
    void rirGateHoldsWhenTheTopWasReachedByGrinding() {
        var gated = line(6, 10, 3, true);
        var baseline = List.of(working("80", 10, 2), working("80", 10, 1), working("80", 10, 0));

        var recommendation = DoubleProgression.recommend(gated, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.HOLD);
        assertThat(recommendation.targets()).containsExactly(target("80", 10), target("80", 10), target("80", 10));
    }

    @Test
    void rirGatePassesWhenEverySetMeetsTheTargetOrHasNoRir() {
        var gated = line(6, 10, 3, true);
        var baseline = List.of(working("80", 10, 2), working("80", 10), working("80", 10, 1));

        var recommendation = DoubleProgression.recommend(gated, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.INCREASE);
    }

    @Test
    void lowRirDoesNotMatterWithTheGateOff() {
        var baseline = List.of(working("80", 10, 0), working("80", 10, 0), working("80", 10, 0));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.INCREASE);
    }

    @Test
    void givesNoTargetWithoutBaseline() {
        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, List.of());

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.NO_BASELINE);
        assertThat(recommendation.targets()).isEmpty();
    }

    @Test
    void warmupsAloneAreNoBaseline() {
        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, List.of(warmup("40", 10)));

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.NO_BASELINE);
    }

    @Test
    void ignoresWarmups() {
        var baseline =
                List.of(warmup("40", 5), working("80", 10), warmup("60", 3), working("80", 10), working("80", 10));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.INCREASE);
        assertThat(recommendation.targets()).hasSize(3);
    }

    @Test
    void holdsWithFewerSetsThanPrescribed() {
        var baseline = List.of(working("80", 10), working("80", 10));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.HOLD);
        assertThat(recommendation.targets()).containsExactly(target("80", 10), target("80", 10), target("80", 10));
    }

    @Test
    void missingPrescribedSetsTakeTheLastLoggedSetsTarget() {
        var fourSets = line(6, 10, 4, false);
        var baseline = List.of(working("80", 9), working("77.5", 7));

        var recommendation = DoubleProgression.recommend(fourSets, baseline);

        assertThat(recommendation.targets())
                .containsExactly(target("80", 10), target("77.5", 8), target("77.5", 8), target("77.5", 8));
    }

    @Test
    void extraSetsNeitherBlockNorGetATarget() {
        var baseline = List.of(working("80", 10), working("80", 10), working("80", 10), working("80", 4));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.INCREASE);
        assertThat(recommendation.targets()).hasSize(3);
    }

    @Test
    void extraSetsAtTheTopDoNotMakeUpForAMissedPrescribedSet() {
        var baseline = List.of(working("80", 10), working("80", 10), working("80", 9), working("80", 10));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.decision()).isEqualTo(Recommendation.Decision.HOLD);
        assertThat(recommendation.targets()).hasSize(3);
    }

    @Test
    void bodyweightAtTheTopAddsTheIncrement() {
        var baseline = List.of(working("0", 10), working("0", 10), working("0", 10));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.targets()).containsExactly(target("2.5", 6), target("2.5", 6), target("2.5", 6));
    }

    @Test
    void targetsKeepEachSetsOwnWeight() {
        var baseline = List.of(working("80", 10), working("77.5", 10), working("75", 10));

        var recommendation = DoubleProgression.recommend(SIX_TO_TEN, baseline);

        assertThat(recommendation.targets()).containsExactly(target("82.5", 6), target("80", 6), target("77.5", 6));
    }

    private static LineSettings line(int repMin, int repMax, int prescribedSets, boolean rirGate) {
        return new LineSettings(repMin, repMax, new BigDecimal("2.5"), 1, 2, prescribedSets, rirGate);
    }

    private static Target target(String weightKg, int reps) {
        return new Target(new BigDecimal(weightKg), reps);
    }

    private static LoggedSet working(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, false, Optional.empty());
    }

    private static LoggedSet working(String weightKg, int completedReps, int rir) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, false, Optional.of(rir));
    }

    private static LoggedSet failed(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, true, Optional.empty());
    }

    private static LoggedSet warmup(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WARMUP, new BigDecimal(weightKg), completedReps, false, Optional.empty());
    }
}
