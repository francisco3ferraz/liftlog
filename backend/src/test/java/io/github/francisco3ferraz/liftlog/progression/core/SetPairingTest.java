package io.github.francisco3ferraz.liftlog.progression.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.progression.core.LoggedSet.Kind;
import io.github.francisco3ferraz.liftlog.progression.core.SetPairing.Mark;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SetPairingTest {

    private static final int REP_MIN = 6;

    @Test
    void pairsWorkingSetsByPosition() {
        var today = List.of(working("80", 9), working("80", 8), working("80", 7));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        var pairing = SetPairing.pair(today, baseline, REP_MIN);

        assertThat(pairing.marks()).containsExactly(Mark.PROGRESSED, Mark.MATCHED, Mark.REGRESSED);
    }

    @Test
    void keepsTheSetsOfEachPair() {
        var today = List.of(working("82.5", 6));
        var baseline = List.of(working("80", 10));

        var pair = SetPairing.pair(today, baseline, REP_MIN).pairs().getFirst();

        assertThat(pair.today()).contains(today.getFirst());
        assertThat(pair.baseline()).contains(baseline.getFirst());
    }

    @Test
    void ignoresWarmupsOnBothSides() {
        var today = List.of(warmup("40", 10), working("80", 9), warmup("60", 5), working("80", 8));
        var baseline = List.of(warmup("40", 12), working("80", 8), working("80", 8));

        var pairing = SetPairing.pair(today, baseline, REP_MIN);

        assertThat(pairing.marks()).containsExactly(Mark.PROGRESSED, Mark.MATCHED);
    }

    @Test
    void marksTodaysUnpairedSetsAsExtra() {
        var today = List.of(working("80", 8), working("80", 8), working("80", 6));
        var baseline = List.of(working("80", 8));

        var pairing = SetPairing.pair(today, baseline, REP_MIN);

        assertThat(pairing.marks()).containsExactly(Mark.MATCHED, Mark.EXTRA, Mark.EXTRA);
        assertThat(pairing.pairs().get(2).baseline()).isEmpty();
    }

    @Test
    void marksBaselinesUnpairedSetsAsMissing() {
        var today = List.of(working("80", 8));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        var pairing = SetPairing.pair(today, baseline, REP_MIN);

        assertThat(pairing.marks()).containsExactly(Mark.MATCHED, Mark.MISSING, Mark.MISSING);
        assertThat(pairing.pairs().get(1).today()).isEmpty();
    }

    @Test
    void marksEverySetExtraWithoutBaseline() {
        var today = List.of(working("80", 8), working("80", 8));

        var pairing = SetPairing.pair(today, List.of(), REP_MIN);

        assertThat(pairing.marks()).containsExactly(Mark.EXTRA, Mark.EXTRA);
    }

    @Test
    void onlyExtraAndMissingAreNeutral() {
        assertThat(Mark.EXTRA.isNeutral()).isTrue();
        assertThat(Mark.MISSING.isNeutral()).isTrue();
        assertThat(Mark.PROGRESSED.isNeutral()).isFalse();
        assertThat(Mark.MATCHED.isNeutral()).isFalse();
        assertThat(Mark.REGRESSED.isNeutral()).isFalse();
    }

    @Test
    void fewerThanPrescribedCountsTodaysWorkingSetsOnly() {
        var today = List.of(warmup("40", 10), working("80", 8), working("80", 8));
        var baseline = List.of(working("80", 8), working("80", 8), working("80", 8));

        var pairing = SetPairing.pair(today, baseline, REP_MIN);

        assertThat(pairing.fewerThanPrescribed(3)).isTrue();
        assertThat(pairing.fewerThanPrescribed(2)).isFalse();
    }

    @Test
    void extraSetsAreNotFewerThanPrescribed() {
        var today = List.of(working("80", 8), working("80", 8), working("80", 8), working("80", 8));

        var pairing = SetPairing.pair(today, List.of(), REP_MIN);

        assertThat(pairing.fewerThanPrescribed(3)).isFalse();
    }

    private static LoggedSet working(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, false, Optional.empty());
    }

    private static LoggedSet warmup(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WARMUP, new BigDecimal(weightKg), completedReps, false, Optional.empty());
    }
}
