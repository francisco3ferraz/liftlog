package io.github.francisco3ferraz.liftlog.progression.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.francisco3ferraz.liftlog.progression.core.LoggedSet.Kind;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SetComparisonTest {

    private static final int REP_MIN = 6;

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void comparesTodayWithBaseline(String description, LoggedSet today, LoggedSet baseline, SetComparison expected) {
        assertThat(SetComparison.compare(today, baseline, REP_MIN)).isEqualTo(expected);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of(
                        "higher weight at repMin progressed", set("82.5", 6), set("80", 8), SetComparison.PROGRESSED),
                Arguments.of(
                        "higher weight above repMin progressed",
                        set("82.5", 9),
                        set("80", 8),
                        SetComparison.PROGRESSED),
                Arguments.of(
                        "equal weight with more reps progressed", set("80", 9), set("80", 8), SetComparison.PROGRESSED),
                Arguments.of(
                        "equal weight with more reps below repMin progressed",
                        set("80", 4),
                        set("80", 3),
                        SetComparison.PROGRESSED),
                Arguments.of("equal weight with equal reps matched", set("80", 8), set("80", 8), SetComparison.MATCHED),
                Arguments.of("weights equal regardless of scale", set("80.00", 8), set("80", 8), SetComparison.MATCHED),
                Arguments.of("lower weight regressed", set("77.5", 12), set("80", 8), SetComparison.REGRESSED),
                Arguments.of(
                        "equal weight with fewer reps regressed", set("80", 7), set("80", 8), SetComparison.REGRESSED),
                Arguments.of(
                        "higher weight below repMin regressed", set("82.5", 5), set("80", 8), SetComparison.REGRESSED),
                Arguments.of("failed rep is not counted", failed("80", 8), set("80", 8), SetComparison.MATCHED),
                Arguments.of(
                        "failed rep does not lift a higher weight to repMin",
                        failed("82.5", 5),
                        set("80", 8),
                        SetComparison.REGRESSED),
                Arguments.of(
                        "lower RIR with equal weight and reps matched",
                        withRir("80", 8, 0),
                        withRir("80", 8, 3),
                        SetComparison.MATCHED),
                Arguments.of(
                        "higher RIR with fewer reps still regressed",
                        withRir("80", 7, 4),
                        withRir("80", 8, 0),
                        SetComparison.REGRESSED));
    }

    private static LoggedSet set(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, false, Optional.empty());
    }

    private static LoggedSet failed(String weightKg, int completedReps) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, true, Optional.empty());
    }

    private static LoggedSet withRir(String weightKg, int completedReps, int rir) {
        return new LoggedSet(Kind.WORKING, new BigDecimal(weightKg), completedReps, false, Optional.of(rir));
    }
}
