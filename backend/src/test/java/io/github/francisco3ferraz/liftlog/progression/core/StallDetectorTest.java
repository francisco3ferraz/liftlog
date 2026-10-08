package io.github.francisco3ferraz.liftlog.progression.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.francisco3ferraz.liftlog.progression.core.LoggedSet.Kind;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StallDetectorTest {

    private static final int REP_MIN = 6;
    private static final int THRESHOLD = 3;

    private static final List<LoggedSet> BASELINE = List.of(working(8), working(8), working(8));

    @Test
    void stallsWhenNoneOfTheLastSessionsProgressed() {
        var sessions = List.of(matched(), regressed(), matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isTrue();
    }

    @Test
    void doesNotStallWhenOneOfTheLastSessionsProgressed() {
        var sessions = List.of(matched(), progressed(), matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isFalse();
    }

    @Test
    void onlyTheLastSessionsCount() {
        var sessions = List.of(progressed(), matched(), matched(), matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isTrue();
    }

    @Test
    void doesNotStallWithFewerSessionsThanTheThreshold() {
        var sessions = List.of(matched(), matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isFalse();
    }

    @Test
    void doesNotStallWithNoSessions() {
        assertThat(StallDetector.isStalled(List.of(), THRESHOLD)).isFalse();
    }

    @Test
    void aProgressedSetWithARegressedSetIsNotProgress() {
        var mixed = SetPairing.pair(List.of(working(9), working(8), working(7)), BASELINE, REP_MIN);
        var sessions = List.of(matched(), mixed, matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isTrue();
    }

    @Test
    void neutralSetsDoNotBlockProgress() {
        var withExtra = SetPairing.pair(List.of(working(9), working(8), working(8), working(5)), BASELINE, REP_MIN);
        var withMissing = SetPairing.pair(List.of(working(9)), BASELINE, REP_MIN);

        assertThat(StallDetector.isStalled(List.of(matched(), matched(), withExtra), THRESHOLD))
                .isFalse();
        assertThat(StallDetector.isStalled(List.of(matched(), matched(), withMissing), THRESHOLD))
                .isFalse();
    }

    @Test
    void aSessionWithNoWorkingSetsDoesNotCountTowardAStall() {
        var sessions = List.of(matched(), noWorkingSets(), matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isFalse();
    }

    @Test
    void aSessionWithNoWorkingSetsDoesNotResetTheRun() {
        var sessions = List.of(matched(), matched(), noWorkingSets(), matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isTrue();
    }

    @Test
    void aSessionWithoutBaselineResetsTheRun() {
        var sessions = List.of(matched(), matched(), firstInLine(), matched(), matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isFalse();
    }

    @Test
    void stallsAgainOnceEnoughSessionsFollowTheReset() {
        var sessions = List.of(firstInLine(), matched(), matched(), matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isTrue();
    }

    @Test
    void warmupsInTheBaselineAreNotABaseline() {
        var warmupOnlyBaseline = List.of(new LoggedSet(Kind.WARMUP, new BigDecimal("40"), 10, false, Optional.empty()));
        var noWorkingBaseline = SetPairing.pair(List.of(working(8)), warmupOnlyBaseline, REP_MIN);
        var sessions = List.of(noWorkingBaseline, matched(), matched());

        assertThat(StallDetector.isStalled(sessions, THRESHOLD)).isFalse();
    }

    @Test
    void aThresholdOfOneLooksAtTheLatestSessionOnly() {
        assertThat(StallDetector.isStalled(List.of(progressed(), matched()), 1)).isTrue();
        assertThat(StallDetector.isStalled(List.of(matched(), progressed()), 1)).isFalse();
    }

    @Test
    void rejectsAThresholdBelowOne() {
        assertThatThrownBy(() -> StallDetector.isStalled(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    private static SetPairing progressed() {
        return SetPairing.pair(List.of(working(9), working(8), working(8)), BASELINE, REP_MIN);
    }

    private static SetPairing matched() {
        return SetPairing.pair(BASELINE, BASELINE, REP_MIN);
    }

    private static SetPairing regressed() {
        return SetPairing.pair(List.of(working(8), working(8), working(7)), BASELINE, REP_MIN);
    }

    private static SetPairing noWorkingSets() {
        var warmupOnly = List.of(new LoggedSet(Kind.WARMUP, new BigDecimal("40"), 10, false, Optional.empty()));
        return SetPairing.pair(warmupOnly, BASELINE, REP_MIN);
    }

    private static SetPairing firstInLine() {
        return SetPairing.pair(BASELINE, List.of(), REP_MIN);
    }

    private static LoggedSet working(int completedReps) {
        return new LoggedSet(Kind.WORKING, new BigDecimal("80"), completedReps, false, Optional.empty());
    }
}
