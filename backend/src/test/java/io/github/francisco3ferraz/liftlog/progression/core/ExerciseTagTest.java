package io.github.francisco3ferraz.liftlog.progression.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.progression.core.LoggedSet.Kind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExerciseTagTest {

    private static final int REP_MIN = 6;
    private static final int PRESCRIBED_SETS = 3;

    @Test
    void progressedWhenSomeSetWentUpAndNoneWentDown() {
        var today = List.of(working("80", 9), working("80", 8), working("80", 8));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.PROGRESSED);
    }

    @Test
    void matchedWhenEverySetIsEqual() {
        var today = List.of(working("80", 8), working("80", 8), working("80", 8));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.MATCHED);
    }

    @Test
    void mixedWhenSetsWentBothUpAndDown() {
        var today = List.of(working("80", 9), working("80", 8), working("80", 7));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.MIXED);
    }

    @Test
    void regressedWhenSomeSetWentDownAndNoneWentUp() {
        var today = List.of(working("80", 8), working("80", 8), working("80", 7));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.REGRESSED);
    }

    @Test
    void firstWithoutBaseline() {
        var today = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, List.of())).isEqualTo(ExerciseTag.FIRST);
    }

    @Test
    void firstWhenBaselineHadOnlyWarmups() {
        var today = List.of(working("80", 8), working("80", 8), working("80", 8));
        var baseline = List.of(warmup("40", 10));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.FIRST);
    }

    @Test
    void fewerSetsThanPrescribedCannotProgress() {
        var today = List.of(working("80", 9), working("80", 9));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.MATCHED);
    }

    @Test
    void fewerSetsThanPrescribedIsStillTaggedOnThePairedSets() {
        var today = List.of(working("80", 9), working("80", 7));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.MIXED);
    }

    @Test
    void fewerSetsThanPrescribedCanStillRegress() {
        var today = List.of(working("80", 7));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.REGRESSED);
    }

    @Test
    void extraAndMissingSetsAreNeutral() {
        var today = List.of(working("80", 8), working("80", 8), working("80", 8), working("80", 5));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.MATCHED);
    }

    @Test
    void progressedWhenTheOnlyUpIsBesideAnExtraSet() {
        var today = List.of(working("80", 8), working("80", 8), working("80", 9), working("80", 4));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.PROGRESSED);
    }

    @Test
    void warmupsAreExcluded() {
        var today = List.of(warmup("40", 3), working("80", 9), working("80", 8), working("80", 8));
        var baseline = List.of(warmup("60", 12), working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.PROGRESSED);
    }

    @Test
    void warmupsDoNotCountTowardsPrescribedSets() {
        var today = List.of(warmup("40", 10), working("80", 9), working("80", 9));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        assertThat(tag(today, baseline)).isEqualTo(ExerciseTag.MATCHED);
    }

    private static ExerciseTag tag(List<LoggedSet> today, List<LoggedSet> baseline) {
        return ExerciseTag.of(SetPairing.pair(today, baseline, REP_MIN), PRESCRIBED_SETS);
    }

    private static LoggedSet working(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, false, Optional.empty());
    }

    private static LoggedSet warmup(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WARMUP, new BigDecimal(weightKg), completedReps, false, Optional.empty());
    }
}
